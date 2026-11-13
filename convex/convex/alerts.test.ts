/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api, internal } from "./_generated/api";
import schema from "./schema";
import { Id } from "./_generated/dataModel";

const modules = import.meta.glob("./**/*.ts");

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-13T10:00:00Z"));
});
afterEach(() => vi.useRealTimers());

const HASH = "a".repeat(64);

async function userWithDevice(t: ReturnType<typeof convexTest>) {
  const { token } = await t.action(api.auth.signUp, {
    turnstileToken: "test-token",
    email: "aditya@example.com",
    password: "password one",
  });
  const { code } = await t.mutation(api.pairing.start, { secretHash: HASH, name: "laptop", os: "windows" });
  const res = (await t.mutation(api.dashboard.confirmPairing, { token, code }))!;
  return { token, deviceId: res.deviceId as Id<"devices"> };
}

// Average and peak both over the defaults (CPU 90, GPU 85, RAM 90), so any sensible rule fires.
const HOT = {
  cpuUtilAvg: 50, cpuUtilMax: 80,
  ramPctAvg: 95, ramPctMax: 97,
  cpuTempAvg: 95, cpuTempMax: 98,
  gpuTempMax: 90, gpuUtilAvg: 60,
};
const CALM = {
  cpuUtilAvg: 10, cpuUtilMax: 20,
  ramPctAvg: 40, ramPctMax: 45,
  cpuTempAvg: 50, cpuTempMax: 55,
  gpuTempMax: 45, gpuUtilAvg: 5,
};
const push = (t: ReturnType<typeof convexTest>, s: typeof HOT) =>
  t.mutation(api.agent.pushSummary, { secretHash: HASH, minuteTs: 0, ...s });

test("a hot summary raises cpuTemp, gpuTemp and ram alerts; cooldown stops repeats (FR-43, FR-48)", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t);

  await push(t, CALM);
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);

  vi.advanceTimersByTime(60_000);
  await push(t, HOT);
  const alerts = await t.query(api.devices.getMyAlerts, { token });
  expect(alerts.map((a) => a.type).sort()).toEqual(["cpuTemp", "gpuTemp", "ram"]);
  expect(alerts.every((a) => a.deviceId === deviceId && a.pushed === false)).toBe(true);
  expect(alerts.find((a) => a.type === "cpuTemp")?.threshold).toBe(90);

  vi.advanceTimersByTime(60_000);
  await push(t, HOT);
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(3);

  vi.advanceTimersByTime(15 * 60_000);
  await push(t, HOT);
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(6);
});

test("a device's own thresholds win over the account-wide ones", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t);
  // Account-wide: very high, so HOT would not alert...
  await t.mutation(api.devices.updateThresholds, { token, cpuTempC: 120, gpuTempC: 120, ramPct: 100 });
  await push(t, HOT);
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);

  // ...but this device's own CPU limit is 60 °C. GPU and RAM still use the account-wide values.
  await t.mutation(api.devices.updateThresholds, { token, deviceId, cpuTempC: 60 });
  vi.advanceTimersByTime(60_000);
  await push(t, CALM); // CPU 50–55 °C: under 60
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);
  vi.advanceTimersByTime(60_000);
  await push(t, HOT);
  const alerts = await t.query(api.devices.getMyAlerts, { token });
  expect(alerts.map((a) => [a.type, a.threshold])).toEqual([["cpuTemp", 60]]);
});

test("a backlog batch alerts on its newest minute only", async () => {
  const t = convexTest(schema, modules);
  const { token } = await userWithDevice(t);
  const now = Date.now();
  await t.mutation(api.agent.pushSummaries, {
    secretHash: HASH,
    sentAt: now,
    summaries: [
      { minuteTs: now - 3 * 60_000, ...HOT },
      { minuteTs: now - 2 * 60_000, ...HOT },
      { minuteTs: now - 60_000, ...CALM },
    ],
  });
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);
});

test("removing a device (uninstall) deletes its alerts and its own thresholds", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t);
  await push(t, HOT);
  await t.mutation(api.devices.updateThresholds, { token, deviceId, cpuTempC: 70 });
  await t.mutation(api.agent.unpair, { secretHash: HASH });
  await t.finishAllScheduledFunctions(vi.runAllTimers);
  expect(await t.run((ctx) => ctx.db.query("alerts").collect())).toHaveLength(0);
  expect(await t.run((ctx) => ctx.db.query("thresholds").collect())).toHaveLength(0);
});

test("the daily cleanup deletes alerts older than 7 days", async () => {
  const t = convexTest(schema, modules);
  await userWithDevice(t);
  await push(t, HOT);
  vi.advanceTimersByTime(8 * 24 * 60 * 60 * 1000);
  const res = await t.mutation(internal.cleanup.deleteExpired, {});
  expect(res.alerts).toBe(3);
  expect(await t.run((ctx) => ctx.db.query("alerts").collect())).toHaveLength(0);
});

const withDisks = (disks: { name: string; freeGB: number; freePct: number }[]) => ({ ...CALM, disks });

test("disk alerts: one per low drive, using per-drive -> device -> account -> 10% limits", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t);

  // Default 10%: only D: (8% free) is low.
  await push(t, withDisks([{ name: "C:\\", freeGB: 100, freePct: 40 }, { name: "D:\\", freeGB: 8, freePct: 8 }]));
  let alerts = await t.query(api.devices.getMyAlerts, { token });
  expect(alerts.map((a) => [a.type, a.drive, a.threshold])).toEqual([["disk", "D:\\", 10]]);

  // C: gets its own 50% limit; the account default drops to 5%, so D: (8%) is fine now.
  await t.mutation(api.devices.setDiskThreshold, { token, deviceId, drive: "C:\\", minFreePct: 50 });
  await t.mutation(api.devices.updateThresholds, { token, diskFreePct: 5 });
  expect(await t.query(api.devices.getDiskThresholds, { token, deviceId })).toEqual([{ drive: "C:\\", minFreePct: 50 }]);
  vi.advanceTimersByTime(60_000);
  await push(t, withDisks([{ name: "C:\\", freeGB: 100, freePct: 40 }, { name: "D:\\", freeGB: 8, freePct: 8 }]));
  alerts = await t.query(api.devices.getMyAlerts, { token });
  expect(alerts.filter((a) => a.drive === "C:\\").map((a) => a.threshold)).toEqual([50]);

  // Same drive again within 15 minutes: no repeat. Removing C:'s own limit falls back to 5%.
  vi.advanceTimersByTime(60_000);
  await push(t, withDisks([{ name: "C:\\", freeGB: 100, freePct: 40 }]));
  expect((await t.query(api.devices.getMyAlerts, { token })).length).toBe(2);
  await t.mutation(api.devices.setDiskThreshold, { token, deviceId, drive: "C:\\", minFreePct: null });
  expect(await t.query(api.devices.getDiskThresholds, { token, deviceId })).toEqual([]);
});

test("per-drive limits are owner-only and range-checked", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t);
  const other = await t.action(api.auth.signUp, { turnstileToken: "test-token", email: "x@example.com", password: "password one" });
  await expect(
    t.mutation(api.devices.setDiskThreshold, { token: other.token, deviceId, drive: "C:\\", minFreePct: 20 }),
  ).rejects.toThrow(/Unauthorized/);
  await expect(t.query(api.devices.getDiskThresholds, { token: other.token, deviceId })).rejects.toThrow(/Unauthorized/);
  await expect(
    t.mutation(api.devices.setDiskThreshold, { token, deviceId, drive: "C:\\", minFreePct: 0 }),
  ).rejects.toThrow(/out of range/);
  await expect(t.mutation(api.devices.updateThresholds, { token, diskFreePct: 95 })).rejects.toThrow(/out of range/);
});

test("a shared computer alerts the owner and everyone it is shared with, each once", async () => {
  const t = convexTest(schema, modules);
  const { token: ownerToken } = await userWithDevice(t);
  const { token: otherToken } = await t.action(api.auth.signUp, {
    turnstileToken: "test-token", email: "family@example.com", password: "password two",
  });
  const { code } = await t.mutation(api.pairing.start, { secretHash: HASH, name: "laptop", os: "windows" });
  await t.mutation(api.dashboard.confirmPairing, { token: otherToken, code });

  await push(t, HOT);
  const forOwner = await t.query(api.devices.getMyAlerts, { token: ownerToken });
  const forOther = await t.query(api.devices.getMyAlerts, { token: otherToken });
  expect(forOwner.map((a) => a.type).sort()).toEqual(["cpuTemp", "gpuTemp", "ram"]);
  expect(forOther.map((a) => a.type).sort()).toEqual(["cpuTemp", "gpuTemp", "ram"]);

  // Each person's own cooldown: another hot minute adds nothing for either of them.
  vi.advanceTimersByTime(60_000);
  await push(t, HOT);
  expect(await t.query(api.devices.getMyAlerts, { token: otherToken })).toHaveLength(3);
});
