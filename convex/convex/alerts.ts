// Threshold alerts (FR-40–43, FR-48; tasks/todo.md Tasks 9–10).
// Evaluated on every summary the agent sends (once a minute, always, not only while someone is
// watching), so an alert fires within about a minute of the threshold being crossed.
// Push delivery (FCM) is not wired yet: alerts are stored and the app lists them.
import type { MutationCtx, QueryCtx } from "./_generated/server";
import type { Doc, Id } from "./_generated/dataModel";

export const DEFAULT_THRESHOLDS = { cpuTempC: 90, gpuTempC: 85, ramPct: 90 };
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
};

export async function thresholdsFor(ctx: Pick<QueryCtx, "db">, userId: Id<"users">): Promise<Thresholds> {
  const row = await ctx.db
    .query("thresholds")
    .withIndex("by_user", (q) => q.eq("userId", userId))
    .unique();
  return row ? { cpuTempC: row.cpuTempC, gpuTempC: row.gpuTempC, ramPct: row.ramPct } : DEFAULT_THRESHOLDS;
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
  for (const b of breaches(s, await thresholdsFor(ctx, userId))) {
    const recent = await ctx.db
      .query("alerts")
      .withIndex("by_device_type_created", (q) =>
        q.eq("deviceId", device._id).eq("type", b.type).gt("createdAt", now - ALERT_COOLDOWN_MS),
      )
      .first();
    if (recent) continue;
    await ctx.db.insert("alerts", {
      userId,
      deviceId: device._id,
      deviceName: device.name,
      type: b.type,
      value: b.value,
      threshold: b.threshold,
      createdAt: now,
    });
    raised++;
  }
  return raised;
}
