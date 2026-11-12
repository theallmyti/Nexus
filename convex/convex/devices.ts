import { query, mutation } from "./_generated/server";
import { v, ConvexError } from "convex/values";
import { requireUser, requireUserAndTouch } from "./model";
import { isLimited, recordEvent, LIMITS } from "./rateLimit";

/**
 * Session-Authenticated Device Management API
 *
 * Adheres strictly to docs/Security.md §2 (Row 31) and Testing.md:108:
 * "Every query/mutation checks device.userId == session.userId"
 *
 * Every endpoint requires `token: v.string()`.
 * Caller identity is derived exclusively from the verified session in `sessions`.
 * No caller-supplied email or userId parameter is ever trusted as a credential.
 */

const OFFLINE_AFTER_MS = 3 * 60 * 1000; // 3-minute grace period per FR-44

function inferOs(device: any): string {
  if (device.os) return device.os;
  if (device.capabilities?.windowsMode) return "windows";
  if (device.capabilities?.isPi) return "pi";
  return "linux";
}

// 1. List caller's devices (strictly scoped to authenticated session user)
export const getMyDevices = query({
  args: {
    token: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const devices = await ctx.db
      .query("devices")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect();

    const now = Date.now();
    const latestList = await Promise.all(
      devices.map((d) =>
        ctx.db
          .query("latest")
          .withIndex("by_device", (q) => q.eq("deviceId", d._id))
          .first()
      )
    );

    return devices.map((d, i) => {
      const l = latestList[i];
      const isOnline =
        (l && now - (l.ts ?? 0) < 45_000) ||
        now - (d.lastSeenAt ?? 0) < OFFLINE_AFTER_MS;
      return {
        ...d,
        os: inferOs(d),
        online: Boolean(isOnline),
        latest: l ?? undefined,
      };
    });
  },
});

// 2. Get single device details with ownership assertion
export const getDevice = query({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
  },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    const now = Date.now();
    const latest = await ctx.db
      .query("latest")
      .withIndex("by_device", (q) => q.eq("deviceId", args.deviceId))
      .first();
    const isOnline =
      (latest && now - (latest.ts ?? 0) < 45_000) ||
      now - (device.lastSeenAt ?? 0) < OFFLINE_AFTER_MS;

    return {
      ...device,
      os: inferOs(device),
      online: Boolean(isOnline),
      latest: latest ?? undefined,
    };
  },
});

// 3. Get latest real-time snapshot for a device with ownership check
export const getDeviceLatest = query({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
  },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    const latest = await ctx.db
      .query("latest")
      .withIndex("by_device", (q) => q.eq("deviceId", args.deviceId))
      .first();

    if (!latest) return null;

    const now = Date.now();
    const isOnline =
      now - (latest.ts ?? 0) < 45_000 ||
      now - (device.lastSeenAt ?? 0) < OFFLINE_AFTER_MS;

    return {
      ...latest,
      isOnline: Boolean(isOnline),
    };
  },
});

// 4. Update device friendly display name (user ownership enforced)
export const updateDeviceName = mutation({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
    name: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    const trimmed = args.name.trim();
    if (trimmed.length === 0) {
      throw new ConvexError("Device name cannot be empty.");
    }

    await ctx.db.patch(args.deviceId, { name: trimmed });
    return { success: true, name: trimmed };
  },
});

// 5. Delete device with cascade cleanup (ownership enforced)
export const deleteDevice = mutation({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    // Cascade delete `latest` snapshot
    const latestDocs = await ctx.db
      .query("latest")
      .withIndex("by_device", (q) => q.eq("deviceId", args.deviceId))
      .collect();
    for (const doc of latestDocs) {
      await ctx.db.delete(doc._id);
    }

    // Cascade delete summaries
    const summaries = await ctx.db
      .query("summaries")
      .withIndex("by_device_time", (q) => q.eq("deviceId", args.deviceId))
      .collect();
    for (const s of summaries) {
      await ctx.db.delete(s._id);
    }

    // Cascade delete diskThresholds
    const diskThresh = await ctx.db
      .query("diskThresholds")
      .withIndex("by_device", (q) => q.eq("deviceId", args.deviceId))
      .collect();
    for (const d of diskThresh) {
      await ctx.db.delete(d._id);
    }

    // Cascade delete device alerts
    const deviceAlerts = await ctx.db
      .query("alerts")
      .withIndex("by_user_time", (q) => q.eq("userId", user._id))
      .filter((q) => q.eq(q.field("deviceId"), args.deviceId))
      .collect();
    for (const a of deviceAlerts) {
      await ctx.db.delete(a._id);
    }

    // Clean up pairing requests
    if (device.secretHash) {
      const pairingDocs = await ctx.db
        .query("pairingRequests")
        .withIndex("by_secretHash", (q) => q.eq("secretHash", device.secretHash))
        .collect();
      for (const doc of pairingDocs) {
        await ctx.db.delete(doc._id);
      }
    }

    await ctx.db.delete(args.deviceId);
    return { success: true };
  },
});

// 6. Set viewer timestamp (tells agent to boost telemetry rate to 1s)
export const setViewer = mutation({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    const viewerUntil = Date.now() + 60_000;
    await ctx.db.patch(args.deviceId, { viewerUntil });
    return { viewerUntil };
  },
});

// 7. Confirm pairing request — binds agent strictly to authenticated session user
export const confirmPairing = mutation({
  args: {
    token: v.string(),
    code: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const key = `wrongCode:${user._id}`;
    if (await isLimited(ctx, key, LIMITS.wrongCode)) {
      throw new ConvexError("Too many wrong pairing codes. Wait a few minutes and try again.");
    }
    const normalizedCode = args.code.trim().toUpperCase();

    const request = await ctx.db
      .query("pairingRequests")
      .withIndex("by_code", (q) => q.eq("code", normalizedCode))
      .first();

    if (!request) {
      await recordEvent(ctx, key);
      return null;
    }

    if (request.status !== "pending") {
      throw new ConvexError(`Pairing request is already ${request.status}.`);
    }

    if (Date.now() > request.expiresAt) {
      throw new ConvexError("Pairing code has expired. Generate a new code from the agent.");
    }

    const hostname = request.name ?? (request as any).deviceInfo?.hostname ?? "Monitored-PC";
    const os = request.os ?? (request as any).deviceInfo?.os ?? "windows";
    const kind = (request as any).deviceInfo?.kind ?? (os === "windows" ? "laptop" : "desktop");

    // Check if user already has this machine hostname
    const existing = await ctx.db
      .query("devices")
      .withIndex("by_user_hostname", (q) =>
        q.eq("userId", user._id).eq("hostname", hostname)
      )
      .first();

    let deviceId = existing?._id;

    if (existing) {
      await ctx.db.patch(existing._id, {
        secretHash: request.secretHash,
        os: os,
        lastSeenAt: Date.now(),
        online: true,
      });
    } else {
      deviceId = await ctx.db.insert("devices", {
        userId: user._id,
        name: hostname,
        hostname: hostname,
        os: os,
        kind: kind,
        secretHash: request.secretHash,
        capabilities: {
          drives: [],
          hasBattery: false,
          hasNvidiaGpu: false,
          isPi: false,
          perCoreTemps: false,
        },
        agentVersion: "0.2.1",
        lastSeenAt: Date.now(),
        online: true,
        viewerUntil: 0,
        pairedAt: Date.now(),
        isFake: false,
      });
    }

    await ctx.db.patch(request._id, {
      status: "confirmed",
      userId: user._id,
      deviceId,
    });

    return { success: true, deviceId, name: hostname, os };
  },
});

// 8. List recent alerts for authenticated user
export const getMyAlerts = query({
  args: {
    token: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    return await ctx.db
      .query("alerts")
      .withIndex("by_user_time", (q) => q.eq("userId", user._id))
      .order("desc")
      .take(20);
  },
});

// 9. Clear all alerts for authenticated user
export const clearAlerts = mutation({
  args: {
    token: v.string(),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const alerts = await ctx.db
      .query("alerts")
      .withIndex("by_user_time", (q) => q.eq("userId", user._id))
      .collect();
    for (const a of alerts) {
      await ctx.db.delete(a._id);
    }
    return { success: true, count: alerts.length };
  },
});

// 10. Update hardware threshold limits for authenticated user (global or per-device)
export const updateThresholds = mutation({
  args: {
    token: v.string(),
    deviceId: v.optional(v.id("devices")),
    cpuTempC: v.optional(v.number()),
    gpuTempC: v.optional(v.number()),
    ramPct: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);

    if (args.deviceId) {
      const dev = await ctx.db.get(args.deviceId);
      if (!dev || dev.userId !== user._id) {
        throw new ConvexError("Unauthorized: device does not belong to session");
      }
    }

    const existing = args.deviceId
      ? await ctx.db
          .query("thresholds")
          .withIndex("by_user_device", (q) =>
            q.eq("userId", user._id).eq("deviceId", args.deviceId)
          )
          .first()
      : await ctx.db
          .query("thresholds")
          .withIndex("by_user", (q) => q.eq("userId", user._id))
          .filter((q) => q.eq(q.field("deviceId"), undefined))
          .first();

    const patch: any = {};
    if (args.cpuTempC !== undefined) patch.cpuTempC = args.cpuTempC;
    if (args.gpuTempC !== undefined) patch.gpuTempC = args.gpuTempC;
    if (args.ramPct !== undefined) patch.ramPct = args.ramPct;

    if (existing) {
      await ctx.db.patch(existing._id, patch);
      return { success: true, id: existing._id };
    } else {
      const id = await ctx.db.insert("thresholds", {
        userId: user._id,
        deviceId: args.deviceId,
        ...patch,
      });
      return { success: true, id };
    }
  },
});

// 11. Get current hardware thresholds (per-device with fallback to global default)
export const getThresholds = query({
  args: {
    token: v.string(),
    deviceId: v.optional(v.id("devices")),
  },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);

    if (args.deviceId) {
      const custom = await ctx.db
        .query("thresholds")
        .withIndex("by_user_device", (q) =>
          q.eq("userId", user._id).eq("deviceId", args.deviceId)
        )
        .first();
      if (custom) {
        return { ...custom, isCustom: true };
      }
    }

    const defaultThresh = await ctx.db
      .query("thresholds")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .filter((q) => q.eq(q.field("deviceId"), undefined))
      .first();

    return defaultThresh ? { ...defaultThresh, isCustom: false } : null;
  },
});

// 11b. Reset custom device thresholds back to global defaults
export const resetDeviceThresholds = mutation({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const dev = await ctx.db.get(args.deviceId);
    if (!dev || dev.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    const custom = await ctx.db
      .query("thresholds")
      .withIndex("by_user_device", (q) =>
        q.eq("userId", user._id).eq("deviceId", args.deviceId)
      )
      .first();
    if (custom) {
      await ctx.db.delete(custom._id);
    }
    return { success: true };
  },
});

// 12. Register FCM push token for authenticated user
export const registerPushToken = mutation({
  args: {
    token: v.string(),
    fcmToken: v.string(),
    phoneModel: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);

    const existing = await ctx.db
      .query("pushTokens")
      .withIndex("by_token", (q) => q.eq("fcmToken", args.fcmToken))
      .first();

    if (existing) {
      await ctx.db.patch(existing._id, {
        userId: user._id,
        phoneModel: args.phoneModel ?? "Android",
        updatedAt: Date.now(),
      });
      return { success: true, id: existing._id };
    } else {
      const id = await ctx.db.insert("pushTokens", {
        userId: user._id,
        fcmToken: args.fcmToken,
        phoneModel: args.phoneModel ?? "Android",
        updatedAt: Date.now(),
      });
      return { success: true, id };
    }
  },
});

// 13. Get historical summaries for a device (ownership enforced)
export const getDeviceSummaries = query({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
    days: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }

    const days = args.days ?? 7;
    const sinceMs = Date.now() - days * 24 * 3600 * 1000;

    return await ctx.db
      .query("summaries")
      .withIndex("by_device_time", (q) =>
        q.eq("deviceId", args.deviceId).gte("minuteTs", sinceMs)
      )
      .order("asc")
      .collect();
  },
});
