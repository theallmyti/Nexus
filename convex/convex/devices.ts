// Endpoints for the Android app (android/.../data/mock/*Repository.kt). Argument names and return
// shapes match what the app sends and parses. Same rules as dashboard.ts: every call needs a
// session token and only touches the caller's own devices (docs/Security.md §2).
import { mutation, query } from "./_generated/server";
import { v, ConvexError } from "convex/values";
import { ownedDevice, removeDeviceData, requireUser, requireUserAndTouch } from "./model";
import { confirmPairingCode, isOnline, listDevices } from "./dashboard";
import { DEFAULT_THRESHOLDS, thresholdsFor } from "./alerts";

const VIEWER_WINDOW_MS = 30 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const MAX_HISTORY_DAYS = 7; // summaries are kept 7 days (summaries.ts)
const MAX_ALERTS = 200;
const MAX_PUSH_TOKENS_PER_USER = 10;

// Same list as dashboard:overview's `devices`, as a bare array (the app's fallback).
export const getMyDevices = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    return await listDevices(ctx, user);
  },
});

// The device's live row plus `isOnline`. `ts: 0` when the agent has never pushed live data.
export const getDeviceLatest = query({
  args: { token: v.string(), deviceId: v.id("devices") },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const device = await ownedDevice(ctx, user, args.deviceId);
    const latest = await ctx.db
      .query("latest")
      .withIndex("by_device", (q) => q.eq("deviceId", device._id))
      .unique();
    return { ...(latest ?? {}), ts: latest?.ts ?? 0, isOnline: isOnline(device, Date.now()) };
  },
});

// Per-minute summaries for the last `days` days (1–7), oldest first.
export const getDeviceSummaries = query({
  args: { token: v.string(), deviceId: v.id("devices"), days: v.number() },
  handler: async (ctx, args) => {
    if (!Number.isFinite(args.days)) throw new ConvexError("invalid days");
    const days = Math.min(Math.max(Math.ceil(args.days), 1), MAX_HISTORY_DAYS);
    const user = await requireUser(ctx, args.token);
    await ownedDevice(ctx, user, args.deviceId);
    const since = Date.now() - days * DAY_MS;
    return await ctx.db
      .query("summaries")
      .withIndex("by_device_time", (q) => q.eq("deviceId", args.deviceId).gte("minuteTs", since))
      .collect();
  },
});

// Keep live data flowing for the next 30 s; the app repeats it while a device screen is open.
export const setViewer = mutation({
  args: { token: v.string(), deviceId: v.id("devices") },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    await ownedDevice(ctx, user, args.deviceId);
    await ctx.db.patch(args.deviceId, { viewerUntil: Date.now() + VIEWER_WINDOW_MS });
  },
});

// Remove a device; its secret hash goes with it, so that computer can no longer write.
export const deleteDevice = mutation({
  args: { token: v.string(), deviceId: v.id("devices") },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const device = await ownedDevice(ctx, user, args.deviceId);
    await removeDeviceData(ctx, device);
  },
});

// Same as dashboard:confirmPairing (same wrong-code limit); returns { deviceId, name, os } or null.
export const confirmPairing = mutation({
  args: { token: v.string(), code: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    return await confirmPairingCode(ctx, user, args.code);
  },
});

// Newest first, at most 200.
export const getMyAlerts = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    return await ctx.db
      .query("alerts")
      .withIndex("by_user_created", (q) => q.eq("userId", user._id))
      .order("desc")
      .take(MAX_ALERTS);
  },
});

export const clearAlerts = mutation({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const rows = await ctx.db
      .query("alerts")
      .withIndex("by_user_created", (q) => q.eq("userId", user._id))
      .take(1000);
    for (const row of rows) {
      await ctx.db.delete(row._id);
    }
    return rows.length;
  },
});

export const getThresholds = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    return await thresholdsFor(ctx, user._id);
  },
});

// FR-41: the app's threshold screen. Ranges match what the agent can report (temps 0–125 °C).
export const updateThresholds = mutation({
  args: { token: v.string(), cpuTempC: v.number(), gpuTempC: v.number(), ramPct: v.number() },
  handler: async (ctx, { token, ...t }) => {
    const user = await requireUserAndTouch(ctx, token);
    const inRange = (x: number, lo: number, hi: number) => Number.isFinite(x) && x >= lo && x <= hi;
    if (!inRange(t.cpuTempC, 30, 125) || !inRange(t.gpuTempC, 30, 125) || !inRange(t.ramPct, 10, 100)) {
      throw new ConvexError("threshold out of range");
    }
    const existing = await ctx.db
      .query("thresholds")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .unique();
    const row = { userId: user._id, ...t, updatedAt: Date.now() };
    if (existing) {
      await ctx.db.replace(existing._id, row);
    } else {
      await ctx.db.insert("thresholds", row);
    }
    return { ...DEFAULT_THRESHOLDS, ...t };
  },
});

// One row per FCM token. A token that moves to another account (phone signed in as someone
// else) is re-linked, so pushes never go to the previous account's phone.
export const registerPushToken = mutation({
  args: { token: v.string(), fcmToken: v.string(), phoneModel: v.optional(v.string()) },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const fcmToken = args.fcmToken.trim();
    if (fcmToken.length < 20 || fcmToken.length > 4096) {
      throw new ConvexError("invalid push token");
    }
    const phoneModel = args.phoneModel?.replace(/[\u0000-\u001F\u007F-\u009F]/g, "").trim().slice(0, 64);
    const now = Date.now();
    const existing = await ctx.db
      .query("pushTokens")
      .withIndex("by_fcmToken", (q) => q.eq("fcmToken", fcmToken))
      .unique();
    if (existing) {
      await ctx.db.patch(existing._id, { userId: user._id, phoneModel, updatedAt: now });
    } else {
      await ctx.db.insert("pushTokens", { userId: user._id, fcmToken, phoneModel, updatedAt: now });
    }
    // Keep the newest few per account; stale tokens from reinstalls are dropped.
    const mine = await ctx.db
      .query("pushTokens")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect();
    mine.sort((a, b) => b.updatedAt - a.updatedAt);
    for (const old of mine.slice(MAX_PUSH_TOKENS_PER_USER)) {
      await ctx.db.delete(old._id);
    }
  },
});
