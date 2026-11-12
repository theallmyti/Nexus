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
