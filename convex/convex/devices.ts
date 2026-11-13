import { query, mutation } from "./_generated/server";
import { v, ConvexError } from "convex/values";
import { canView, deviceBySecretHash, leaveDevice, removeDeviceData, requireUser, requireUserAndTouch, shareDevice, sharedDevicesOf } from "./model";
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

// The device's secret hash is the agent's credential: never send it to a client (with sharing,
// other accounts read these responses and could otherwise pose as the computer).
function withoutSecret<T extends { secretHash?: string }>(d: T): Omit<T, "secretHash"> {
  const { secretHash: _secret, ...rest } = d;
  return rest;
}

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
    const owned = await ctx.db
      .query("devices")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect();
    const devices = [...owned, ...(await sharedDevicesOf(ctx, user._id))];

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
        ...withoutSecret(d),
        os: inferOs(d),
        online: Boolean(isOnline),
        latest: l ?? undefined,
        shared: d.userId !== user._id,
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
    if (!device || !(await canView(ctx, user, device))) {
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
      ...withoutSecret(device),
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
    if (!device || !(await canView(ctx, user, device))) {
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
    if (!device || !(await canView(ctx, user, device))) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }
    if (device.userId !== user._id) {
      await leaveDevice(ctx, device._id, user._id);
      return { success: true, left: true };
    }
    // Owner: the computer, its data, and everyone's access go together.
    await removeDeviceData(ctx, device);
    return { success: true };
  },
});

// Who can see this computer besides the owner (owner only).
export const getDeviceShares = query({
  args: { token: v.string(), deviceId: v.id("devices") },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }
    const shares = await ctx.db
      .query("deviceShares")
      .withIndex("by_device", (q) => q.eq("deviceId", device._id))
      .collect();
    const people = await Promise.all(shares.map((sh) => ctx.db.get(sh.userId)));
    return shares.map((sh, i) => ({
      userId: sh.userId,
      name: people[i]?.name ?? null,
      email: people[i]?.email ?? null,
      sharedAt: sh.sharedAt,
    }));
  },
});

// Owner removes someone's access.
export const removeDeviceShare = mutation({
  args: { token: v.string(), deviceId: v.id("devices"), userId: v.id("users") },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || device.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }
    await leaveDevice(ctx, device._id, args.userId);
    return { success: true };
  },
});

export const setViewer = mutation({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const device = await ctx.db.get(args.deviceId);
    if (!device || !(await canView(ctx, user, device))) {
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

    // Same computer (same secret) already on another account: share it with this account.
    const ownedElsewhere = await deviceBySecretHash(ctx, request.secretHash);
    if (ownedElsewhere && ownedElsewhere.userId && ownedElsewhere.userId !== user._id) {
      await shareDevice(ctx, ownedElsewhere, user._id);
      await ctx.db.patch(request._id, { status: "confirmed", userId: user._id, deviceId: ownedElsewhere._id });
      return { success: true, deviceId: ownedElsewhere._id, name: ownedElsewhere.name, os: inferOs(ownedElsewhere), shared: true };
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
    diskFreePct: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    if (args.diskFreePct !== undefined && !(args.diskFreePct >= 1 && args.diskFreePct <= 90)) {
      throw new ConvexError("disk threshold out of range");
    }

    if (args.deviceId) {
      const dev = await ctx.db.get(args.deviceId);
      if (!dev || !(await canView(ctx, user, dev))) {
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
    if (args.diskFreePct !== undefined) patch.diskFreePct = args.diskFreePct;

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
    if (!dev || !(await canView(ctx, user, dev))) {
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

// 11c. Per-drive disk limits for one device (Threshold settings → Per Drive Settings).
export const getDiskThresholds = query({
  args: { token: v.string(), deviceId: v.id("devices") },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const dev = await ctx.db.get(args.deviceId);
    if (!dev || !(await canView(ctx, user, dev))) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }
    const rows = await ctx.db
      .query("diskThresholds")
      .withIndex("by_device", (q) => q.eq("deviceId", args.deviceId))
      .collect();
    return rows.map((r) => ({ drive: r.drive, minFreePct: r.minFreePct ?? null }));
  },
});

// Set one drive's limit (% free); minFreePct null removes it, so the drive uses the default again.
export const setDiskThreshold = mutation({
  args: {
    token: v.string(),
    deviceId: v.id("devices"),
    drive: v.string(),
    minFreePct: v.union(v.number(), v.null()),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const dev = await ctx.db.get(args.deviceId);
    if (!dev || dev.userId !== user._id) {
      throw new ConvexError("Unauthorized: device does not belong to session");
    }
    const drive = args.drive.replace(/[\u0000-\u001F\u007F-\u009F]/g, "").trim().slice(0, 64);
    if (!drive) throw new ConvexError("invalid drive");
    if (args.minFreePct !== null && !(args.minFreePct >= 1 && args.minFreePct <= 90)) {
      throw new ConvexError("disk threshold out of range");
    }
    const existing = await ctx.db
      .query("diskThresholds")
      .withIndex("by_device_drive", (q) => q.eq("deviceId", args.deviceId).eq("drive", drive))
      .first();
    if (args.minFreePct === null) {
      if (existing) await ctx.db.delete(existing._id);
    } else if (existing) {
      await ctx.db.patch(existing._id, { minFreePct: args.minFreePct });
    } else {
      await ctx.db.insert("diskThresholds", { deviceId: args.deviceId, drive, minFreePct: args.minFreePct });
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
    if (!device || !(await canView(ctx, user, device))) {
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
