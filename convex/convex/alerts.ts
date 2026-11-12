// Threshold alerts (FR-40–43, FR-48; tasks/todo.md Tasks 9–10).
// Evaluated on every summary the agent sends (once a minute, always, not only while someone is
// watching), so an alert fires within about a minute of the threshold being crossed.
// Thresholds come from the app's settings (devices:updateThresholds): a device's own row, then the
// account-wide row (no deviceId), then the defaults below, field by field.
// Rows are written with `pushed: false`; FCM delivery is not wired yet.
import type { MutationCtx, QueryCtx } from "./_generated/server";
import type { Doc, Id } from "./_generated/dataModel";

export const DEFAULT_THRESHOLDS = { cpuTempC: 90, gpuTempC: 85, ramPct: 90 };
// Alert when a drive has less than this % free (FR-42).
export const DEFAULT_DISK_FREE_PCT = 10;
export type Thresholds = typeof DEFAULT_THRESHOLDS;

// FR-48: at most one alert per device and type every 15 minutes.
export const ALERT_COOLDOWN_MS = 15 * 60 * 1000;
export const ALERT_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;

export type AlertType = "cpuTemp" | "gpuTemp" | "ram";
export type Breach = { type: AlertType; value: number; threshold: number };

// The per-minute fields a summary carries (agent.ts summaryFields).
export type SummaryReadings = {
  cpuUtilAvg: number;
  cpuUtilMax: number;
  ramPctAvg: number;
  ramPctMax: number;
  cpuTempAvg?: number;
  cpuTempMax?: number;
  gpuTempMax?: number;
  gpuUtilAvg?: number;
  disks?: { name: string; freeGB: number; freePct: number }[];
};

export async function thresholdsFor(
  ctx: Pick<QueryCtx, "db">,
  userId: Id<"users">,
  deviceId: Id<"devices">,
): Promise<Thresholds> {
  const own = await ctx.db
    .query("thresholds")
    .withIndex("by_user_device", (q) => q.eq("userId", userId).eq("deviceId", deviceId))
    .first();
  const account = await ctx.db
    .query("thresholds")
    .withIndex("by_user_device", (q) => q.eq("userId", userId).eq("deviceId", undefined))
    .first();
  return {
    cpuTempC: own?.cpuTempC ?? account?.cpuTempC ?? DEFAULT_THRESHOLDS.cpuTempC,
    gpuTempC: own?.gpuTempC ?? account?.gpuTempC ?? DEFAULT_THRESHOLDS.gpuTempC,
    ramPct: own?.ramPct ?? account?.ramPct ?? DEFAULT_THRESHOLDS.ramPct,
  };
}

// Drives below their free-space limit. Limit per drive: its own row (Per Drive Settings), then
// the device's disk default, then the account's, then DEFAULT_DISK_FREE_PCT.
export async function diskBreaches(
  ctx: Pick<QueryCtx, "db">,
  userId: Id<"users">,
  deviceId: Id<"devices">,
  disks: { name: string; freePct: number }[],
): Promise<{ drive: string; value: number; threshold: number }[]> {
  if (disks.length === 0) return [];
  const own = await ctx.db
    .query("thresholds")
    .withIndex("by_user_device", (q) => q.eq("userId", userId).eq("deviceId", deviceId))
    .first();
  const account = await ctx.db
    .query("thresholds")
    .withIndex("by_user_device", (q) => q.eq("userId", userId).eq("deviceId", undefined))
    .first();
  const fallback = own?.diskFreePct ?? account?.diskFreePct ?? DEFAULT_DISK_FREE_PCT;
  const perDrive = new Map(
    (
      await ctx.db
        .query("diskThresholds")
        .withIndex("by_device", (q) => q.eq("deviceId", deviceId))
        .collect()
    ).map((r) => [r.drive, r.minFreePct]),
  );
  const out: { drive: string; value: number; threshold: number }[] = [];
  for (const d of disks) {
    const threshold = perDrive.get(d.name) ?? fallback;
    if (d.freePct < threshold) out.push({ drive: d.name, value: d.freePct, threshold });
  }
  return out;
}

// Which thresholds this minute crossed. A summary holds both the minute's average and its peak
// for most readings; which one counts decides how jumpy alerts are.
export function breaches(s: SummaryReadings, t: Thresholds): Breach[] {
  const out: Breach[] = [];
  // TODO(human): push a Breach for each of cpuTemp, gpuTemp and ram that crossed its threshold.
  return out;
}

// Store an alert for each breach, unless the same device + type alerted in the last 15 minutes.
export async function evaluateSummary(ctx: MutationCtx, device: Doc<"devices">, s: SummaryReadings) {
  if (!device.userId) return 0; // ownerless devices belong to nobody, so nobody is alerted
  const userId = device.userId;
  const now = Date.now();
  let raised = 0;
  for (const b of breaches(s, await thresholdsFor(ctx, userId, device._id))) {
    const recent = await ctx.db
      .query("alerts")
      .withIndex("by_device_type_time", (q) =>
        q.eq("deviceId", device._id).eq("type", b.type).gt("createdAt", now - ALERT_COOLDOWN_MS),
      )
      .first();
    if (recent) continue;
    await ctx.db.insert("alerts", {
      userId,
      deviceId: device._id,
      type: b.type,
      value: b.value,
      threshold: b.threshold,
      createdAt: now,
      pushed: false,
    });
    raised++;
  }

  // Disks: one cooldown per drive.
  for (const d of await diskBreaches(ctx, userId, device._id, s.disks ?? [])) {
    const recent = await ctx.db
      .query("alerts")
      .withIndex("by_device_type_time", (q) =>
        q.eq("deviceId", device._id).eq("type", "disk").gt("createdAt", now - ALERT_COOLDOWN_MS),
      )
      .collect();
    if (recent.some((a) => a.drive === d.drive)) continue;
    await ctx.db.insert("alerts", {
      userId,
      deviceId: device._id,
      type: "disk",
      value: d.value,
      threshold: d.threshold,
      drive: d.drive,
      createdAt: now,
      pushed: false,
    });
    raised++;
  }
  return raised;
}
