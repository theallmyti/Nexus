// Agent endpoints (docs/Architecture.md §3.1, owner: Aditya).
// Auth:
//  - hello, pushLatest, pushSummary, watchViewer, unpair: the agent sends SHA-256(device secret);
//    Convex compares the hash (docs/Flow.md §3, docs/Security.md §6).
// Devices are enrolled exclusively through the pairing flow (pairing.ts).
import { mutation, query, type MutationCtx } from "./_generated/server";
import type { Id } from "./_generated/dataModel";
import { deviceBySecretHash, removeDeviceData } from "./model";
import { LATEST_AGENT_VERSION, isUnsupported } from "./versions";
import { RETENTION_MS } from "./summaries";
import { evaluateSummary } from "./alerts";
import { v, ConvexError, type Infer } from "convex/values";
import { battery, capabilities, collectorStatus, driveLive, driveSummary, ramDetail, topProc, usage } from "./validators";

// docs/Security.md §2: at most one summary per 50 s per device.
const SUMMARY_MIN_GAP_MS = 50_000;

function validateSecretHash(secretHash: string) {
  if (!/^[0-9a-f]{64}$/.test(secretHash)) {
    throw new ConvexError("invalid device key");
  }
}

// Called at every agent start once the device is paired: refreshes what it reports (FR-15).
// Never creates a device; pairing does that.
export const hello = mutation({
  args: {
    secretHash: v.string(),
    capabilities: v.optional(capabilities),
    agentVersion: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    validateSecretHash(args.secretHash);
    if (isUnsupported(args.agentVersion)) {
      throw new ConvexError(
        `This agent (${args.agentVersion}) is too old. Install version ${LATEST_AGENT_VERSION} from Nexus's releases.`,
      );
    }
    const device = await deviceBySecretHash(ctx, args.secretHash);
    if (!device) {
      throw new ConvexError("not paired");
    }
    const sanitizedVersion = args.agentVersion
      ? args.agentVersion.replace(/[\u0000-\u001F\u007F-\u009F]/g, "").trim().slice(0, 32)
      : undefined;
    let sanitizedCapabilities = args.capabilities;
    if (sanitizedCapabilities) {
      if (sanitizedCapabilities.drives && sanitizedCapabilities.drives.length > 64) {
        sanitizedCapabilities = { ...sanitizedCapabilities, drives: sanitizedCapabilities.drives.slice(0, 64) };
      }
      if (sanitizedCapabilities.cpuModel) {
        sanitizedCapabilities = {
          ...sanitizedCapabilities,
          cpuModel: sanitizedCapabilities.cpuModel.replace(/[\u0000-\u001F\u007F-\u009F]/g, "").trim().slice(0, 128),
        };
      }
    }
    await ctx.db.patch(device._id, {
      capabilities: sanitizedCapabilities,
      agentVersion: sanitizedVersion,
      lastSeenAt: Date.now(),
    });
    // owned = linked to an account. Devices paired before accounts existed have no owner and
    // are shown to nobody until they are paired again (docs/Security.md §2).
    // latestVersion: the agent logs when a newer release exists (versions.ts).
    return { deviceId: device._id, owned: device.userId !== undefined, latestVersion: LATEST_AGENT_VERSION };
  },
});

// Uninstall: the agent removes its own device, live row, pairing codes and history.
// Authenticated by the device secret hash; unknown devices are a no-op (already removed).
export const unpair = mutation({
  args: { secretHash: v.string() },
  handler: async (ctx, args) => {
    validateSecretHash(args.secretHash);
    const device = await deviceBySecretHash(ctx, args.secretHash);
    if (!device) {
      return false;
    }
    await removeDeviceData(ctx, device);
    return true;
  },
});

export const pushLatest = mutation({
  args: {
    secretHash: v.string(),
    ts: v.number(),
    cpuUtil: v.number(),
    ramUsedPct: v.number(),
    cpuTemp: v.optional(v.number()),
    docker: v.optional(v.object({ running: v.number(), stopped: v.number(), errored: v.number() })),
    perCoreUtil: v.optional(v.array(v.number())),
    clocks: v.optional(v.array(v.number())),
    ram: v.optional(ramDetail),
    topProcs: v.optional(v.array(topProc)),
    disks: v.optional(v.array(driveLive)),
    uptimeSec: v.optional(v.number()),
    usage: v.optional(usage),
    battery: v.optional(battery),
    gpu: v.optional(
      v.object({
        temp: v.number(),
        util: v.number(),
        vramUsed: v.number(),
        vramTotal: v.number(),
        powerW: v.number(),
        clock: v.number(),
        fanPct: v.optional(v.number()),
      }),
    ),
  },
  handler: async (ctx, args) => {
    validateSecretHash(args.secretHash);
    // FR-28: reject impossible values.
    if (args.cpuUtil < 0 || args.cpuUtil > 100 || args.ramUsedPct < 0 || args.ramUsedPct > 100) {
      throw new ConvexError("value out of range");
    }
    if (args.cpuTemp !== undefined && (args.cpuTemp < 0 || args.cpuTemp > 125)) {
      throw new ConvexError("cpu temp out of range");
    }
    if (args.gpu && (args.gpu.temp < 0 || args.gpu.temp > 125 || args.gpu.util < 0 || args.gpu.util > 100)) {
      throw new ConvexError("gpu value out of range");
    }
    if (args.perCoreUtil && (args.perCoreUtil.length > 512 || args.perCoreUtil.some((p) => p < 0 || p > 100))) {
      throw new ConvexError("per-core value out of range");
    }
    if (args.clocks && (args.clocks.length > 512 || args.clocks.some((c) => c < 0 || c > 20_000))) {
      throw new ConvexError("clocks value out of range");
    }
    if (args.disks && (args.disks.length > 64 || args.disks.some((d) => d.freeGB < 0 || d.freeGB > d.totalGB))) {
      throw new ConvexError("disk value out of range");
    }
    if (args.topProcs && (args.topProcs.length > 100 || args.topProcs.some((p) => p.mb < 0 || p.name.length > 256))) {
      throw new ConvexError("process value out of range");
    }
    if (
      args.ram &&
      (args.ram.used < 0 ||
        args.ram.free < 0 ||
        args.ram.total < 0 ||
        args.ram.swapUsed < 0 ||
        args.ram.swapTotal < 0)
    ) {
      throw new ConvexError("ram value out of range");
    }
    if (
      args.docker &&
      (args.docker.running < 0 || args.docker.stopped < 0 || args.docker.errored < 0)
    ) {
      throw new ConvexError("docker count out of range");
    }
    if (args.uptimeSec !== undefined && (args.uptimeSec < 0 || !Number.isFinite(args.uptimeSec))) {
      throw new ConvexError("uptime value out of range");
    }
    if (args.battery && (args.battery.pct < 0 || args.battery.pct > 100)) {
      throw new ConvexError("battery value out of range");
    }
    if (args.usage && args.usage.activeTimeSec < 0) {
      throw new ConvexError("usage value out of range");
    }
    const device = await deviceBySecretHash(ctx, args.secretHash);
    if (!device) {
      throw new ConvexError("unknown device");
    }
    const existing = await ctx.db
      .query("latest")
      .withIndex("by_device", (q) => q.eq("deviceId", device._id))
      .unique();
    // lastSeenAt is set by summaries only (Architecture.md §2.2), not per live push.
    const row = {
      deviceId: device._id,
      // Server time: the site shows "last update" from it, and a PC's clock may be wrong.
      // args.ts (the PC's time) is kept in the arguments for older agents.
      ts: Date.now(),
      cpu: { util: args.cpuUtil, packageTemp: args.cpuTemp, perCoreUtil: args.perCoreUtil, clocks: args.clocks },
      ram: { usedPct: args.ramUsedPct, detail: args.ram, topProcs: args.topProcs },
      disks: args.disks ?? existing?.disks,
      uptimeSec: args.uptimeSec ?? existing?.uptimeSec,
      usage: args.usage ?? existing?.usage,
      battery: args.battery ?? existing?.battery,
      gpu: args.gpu ?? existing?.gpu,
      pi: args.docker ? { docker: args.docker } : existing?.pi,
    };
    if (existing) {
      await ctx.db.replace(existing._id, row);
    } else {
      await ctx.db.insert("latest", row);
    }
  },
});

const MINUTE_MS = 60_000;

// The minute a summary belongs to, by the server's clock: a PC with a wrong clock must not put
// its history in the wrong place, or out of reach of the 7-day cleanup (docs/Security.md §2).
export function serverMinute(now: number) {
  return Math.floor(now / MINUTE_MS) * MINUTE_MS;
}

const summaryFields = {
  cpuUtilAvg: v.number(),
  cpuUtilMax: v.number(),
  ramPctAvg: v.number(),
  ramPctMax: v.number(),
  cpuTempAvg: v.optional(v.number()),
  cpuTempMax: v.optional(v.number()),
  gpuTempMax: v.optional(v.number()),
  gpuUtilAvg: v.optional(v.number()),
  disks: v.optional(v.array(driveSummary)),
  batteryPct: v.optional(v.number()),
  collectorStatus: v.optional(collectorStatus),
};
const summaryValidator = v.object(summaryFields);
type SummaryFields = Infer<typeof summaryValidator>;

// FR-28: reject impossible values.
function checkSummary(summary: SummaryFields) {
  if (
    summary.disks &&
    (summary.disks.length > 64 ||
      summary.disks.some((d) => d.freeGB < 0 || d.freePct < 0 || d.freePct > 100))
  ) {
    throw new ConvexError("disk value out of range");
  }
  const percents = [
    summary.cpuUtilAvg,
    summary.cpuUtilMax,
    summary.ramPctAvg,
    summary.ramPctMax,
    summary.gpuUtilAvg,
    summary.batteryPct,
  ];
  const temps = [summary.cpuTempAvg, summary.cpuTempMax, summary.gpuTempMax];
  if (
    percents.some((p) => p !== undefined && (p < 0 || p > 100)) ||
    temps.some((t) => t !== undefined && (t < 0 || t > 125))
  ) {
    throw new ConvexError("value out of range");
  }
}

// One row per device and minute: a second summary for the same minute replaces the first, so a
// retried send never makes a duplicate (idempotent).
async function storeSummary(ctx: MutationCtx, deviceId: Id<"devices">, minuteTs: number, summary: SummaryFields) {
  const existing = await ctx.db
    .query("summaries")
    .withIndex("by_device_time", (q) => q.eq("deviceId", deviceId).eq("minuteTs", minuteTs))
    .first();
  if (existing) {
    await ctx.db.replace(existing._id, { deviceId, minuteTs, ...summary });
  } else {
    await ctx.db.insert("summaries", { deviceId, minuteTs, ...summary });
  }
}

// Every 60 s, always: the minute's averages and peaks (docs/Architecture.md §2.2, FR-30).
// `minuteTs` from the agent is ignored (its clock may be wrong); kept so older agents still work.
export const pushSummary = mutation({
  args: { secretHash: v.string(), minuteTs: v.number(), ...summaryFields },
  handler: async (ctx, { secretHash, minuteTs: _agentMinute, ...summary }) => {
    validateSecretHash(secretHash);
    checkSummary(summary);
    const device = await deviceBySecretHash(ctx, secretHash);
    if (!device) {
      throw new ConvexError("unknown device");
    }
    const now = Date.now();
    if (device.lastSummaryAt !== undefined && now - device.lastSummaryAt < SUMMARY_MIN_GAP_MS) {
      throw new ConvexError("summary sent too soon");
    }
    await storeSummary(ctx, device._id, serverMinute(now), summary);
    await ctx.db.patch(device._id, { lastSeenAt: now, lastSummaryAt: now });
    await evaluateSummary(ctx, device, summary);
  },
});

// Agents from 0.2.0: the summaries not sent yet, oldest first, in one call per minute. While a
// computer is offline the agent keeps them (up to a day) and sends the backlog when it is back.
// Times: the agent stamps each minute with its own clock and sends `sentAt` (its clock now); the
// server shifts every minute by (server now - sentAt), so a wrong PC clock does not matter.
// Rows that are impossible, in the future or older than the 7-day retention are skipped (counted)
// instead of failing the batch, so one bad row cannot block the backlog. A minute that is already
// stored is replaced, so a retried batch never makes duplicates.
const MAX_BATCH = 60;

export const pushSummaries = mutation({
  args: {
    secretHash: v.string(),
    sentAt: v.number(),
    summaries: v.array(v.object({ minuteTs: v.number(), ...summaryFields })),
  },
  handler: async (ctx, args) => {
    validateSecretHash(args.secretHash);
    if (args.summaries.length === 0 || args.summaries.length > MAX_BATCH) {
      throw new ConvexError(`send 1 to ${MAX_BATCH} summaries per call`);
    }
    const device = await deviceBySecretHash(ctx, args.secretHash);
    if (!device) {
      throw new ConvexError("unknown device");
    }
    const now = Date.now();
    // docs/Security.md §2: one call per 50 s. Not an error: the agent keeps the batch and retries.
    if (device.lastSummaryAt !== undefined && now - device.lastSummaryAt < SUMMARY_MIN_GAP_MS) {
      return { stored: 0, skipped: 0, tooSoon: true };
    }
    const offset = now - args.sentAt;
    const newest = serverMinute(now);
    const oldest = now - RETENTION_MS;
    let stored = 0;
    let skipped = 0;
    // Only the newest stored minute is checked for alerts: a backlog sent after a day offline
    // should not raise an alert for every old minute.
    let newestStored: { minute: number; summary: SummaryFields } | undefined;
    for (const { minuteTs, ...summary } of args.summaries) {
      const minute = serverMinute(minuteTs + offset);
      try {
        checkSummary(summary);
      } catch {
        skipped++;
        continue;
      }
      if (minute > newest || minute < oldest) {
        skipped++;
        continue;
      }
      await storeSummary(ctx, device._id, minute, summary);
      stored++;
      if (!newestStored || minute >= newestStored.minute) newestStored = { minute, summary };
    }
    await ctx.db.patch(device._id, { lastSeenAt: now, lastSummaryAt: now });
    if (newestStored) await evaluateSummary(ctx, device, newestStored.summary);
    return { stored, skipped, tooSoon: false };
  },
});

// The agent subscribes to this; live pushes run only while the value is in the future (FR-31).
export const watchViewer = query({
  args: { secretHash: v.string() },
  handler: async (ctx, args) => {
    validateSecretHash(args.secretHash);
    const device = await deviceBySecretHash(ctx, args.secretHash);
    if (!device) {
      throw new ConvexError("unknown device");
    }
    return device.viewerUntil ?? 0;
  },
});
