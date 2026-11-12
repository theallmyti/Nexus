/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api, internal } from "./_generated/api";
import schema from "./schema";
import { Id } from "./_generated/dataModel";

const modules = import.meta.glob("./**/*.ts");

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-12T10:00:00Z"));
});
afterEach(() => vi.useRealTimers());

const HASH_A = "a".repeat(64);
const HASH_B = "b".repeat(64);

async function userWithDevice(t: ReturnType<typeof convexTest>, email: string, hash: string) {
  const { token } = await t.action(api.auth.signUp, { turnstileToken: "test-token", email, password: "password one" });
  const { code } = await t.mutation(api.pairing.start, { secretHash: hash, name: email, os: "windows" });
  const res = await t.mutation(api.devices.confirmPairing, { token, code });
  return { token, deviceId: res!.deviceId as Id<"devices">, pairing: res! };
}

// A hot minute: both average and peak are over the default thresholds (CPU 90, GPU 85, RAM 90).
const HOT = {
  cpuUtilAvg: 50, cpuUtilMax: 80,
  ramPctAvg: 95, ramPctMax: 97,
  cpuTempAvg: 95, cpuTempMax: 98,
  gpuTempMax: 90, gpuUtilAvg: 60,
};
// A calm minute: everything well below.
const CALM = {
  cpuUtilAvg: 10, cpuUtilMax: 20,
  ramPctAvg: 40, ramPctMax: 45,
  cpuTempAvg: 50, cpuTempMax: 55,
  gpuTempMax: 45, gpuUtilAvg: 5,
};

test("confirmPairing returns the device id, name and os the app shows", async () => {
  const t = convexTest(schema, modules);
  const { pairing } = await userWithDevice(t, "aditya@example.com", HASH_A);
  expect(pairing).toMatchObject({ name: "aditya@example.com", os: "windows" });
});

test("app endpoints only ever touch the caller's own devices", async () => {
  const t = convexTest(schema, modules);
  const a = await userWithDevice(t, "aditya@example.com", HASH_A);
  const b = await userWithDevice(t, "tirth@example.com", HASH_B);

  const mine = await t.query(api.devices.getMyDevices, { token: a.token });
  expect(mine.map((d) => d.id)).toEqual([a.deviceId]);
  expect(mine[0].os).toBe("windows");

  for (const call of [
    () => t.query(api.devices.getDeviceLatest, { token: b.token, deviceId: a.deviceId }),
    () => t.query(api.devices.getDeviceSummaries, { token: b.token, deviceId: a.deviceId, days: 1 }),
    () => t.mutation(api.devices.setViewer, { token: b.token, deviceId: a.deviceId }),
    () => t.mutation(api.devices.deleteDevice, { token: b.token, deviceId: a.deviceId }),
  ]) {
    await expect(call()).rejects.toThrow(/device not found/);
  }
  await expect(t.query(api.devices.getMyDevices, { token: "nope" })).rejects.toThrow(/signed out/);
});

test("getDeviceLatest returns the live row with isOnline, or ts 0 before any push", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t, "aditya@example.com", HASH_A);

  const empty = await t.query(api.devices.getDeviceLatest, { token, deviceId });
  expect(empty).toMatchObject({ ts: 0, isOnline: true });

  await t.mutation(api.agent.pushLatest, { secretHash: HASH_A, ts: 0, cpuUtil: 42, ramUsedPct: 61, cpuTemp: 70 });
  const live = await t.query(api.devices.getDeviceLatest, { token, deviceId });
  expect(live).toMatchObject({ cpu: { util: 42, packageTemp: 70 }, ram: { usedPct: 61 }, isOnline: true });
  expect(live.ts).toBeGreaterThan(0);

  vi.advanceTimersByTime(4 * 60 * 1000);
  expect((await t.query(api.devices.getDeviceLatest, { token, deviceId })).isOnline).toBe(false);
});

test("getDeviceSummaries returns the last N days, capped at 7", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t, "aditya@example.com", HASH_A);
  const day = 24 * 60 * 60 * 1000;
  await t.run(async (ctx) => {
    for (const ago of [0.5, 3, 6.5, 9]) {
      await ctx.db.insert("summaries", { deviceId, minuteTs: Date.now() - ago * day, ...CALM });
    }
  });
  expect(await t.query(api.devices.getDeviceSummaries, { token, deviceId, days: 1 })).toHaveLength(1);
  expect(await t.query(api.devices.getDeviceSummaries, { token, deviceId, days: 30 })).toHaveLength(3);
});

test("thresholds default, can be changed, and reject impossible values", async () => {
  const t = convexTest(schema, modules);
  const { token } = await userWithDevice(t, "aditya@example.com", HASH_A);
  expect(await t.query(api.devices.getThresholds, { token })).toEqual({ cpuTempC: 90, gpuTempC: 85, ramPct: 90 });

  await t.mutation(api.devices.updateThresholds, { token, cpuTempC: 80, gpuTempC: 75, ramPct: 85 });
  await t.mutation(api.devices.updateThresholds, { token, cpuTempC: 81, gpuTempC: 75, ramPct: 85 });
  expect(await t.query(api.devices.getThresholds, { token })).toEqual({ cpuTempC: 81, gpuTempC: 75, ramPct: 85 });
  expect(await t.run((ctx) => ctx.db.query("thresholds").collect())).toHaveLength(1);

  await expect(
    t.mutation(api.devices.updateThresholds, { token, cpuTempC: 500, gpuTempC: 75, ramPct: 85 }),
  ).rejects.toThrow(/out of range/);
});

test("a hot summary raises cpuTemp, gpuTemp and ram alerts; cooldown stops repeats (FR-43, FR-48)", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t, "aditya@example.com", HASH_A);

  await t.mutation(api.agent.pushSummary, { secretHash: HASH_A, minuteTs: 0, ...CALM });
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);

  vi.advanceTimersByTime(60_000);
  await t.mutation(api.agent.pushSummary, { secretHash: HASH_A, minuteTs: 0, ...HOT });
  const alerts = await t.query(api.devices.getMyAlerts, { token });
  expect(alerts.map((a) => a.type).sort()).toEqual(["cpuTemp", "gpuTemp", "ram"]);
  expect(alerts.every((a) => a.deviceId === deviceId && a.deviceName === "aditya@example.com")).toBe(true);
  expect(alerts.find((a) => a.type === "cpuTemp")?.threshold).toBe(90);

  // Still hot a minute later: inside the 15 min cooldown, nothing new.
  vi.advanceTimersByTime(60_000);
  await t.mutation(api.agent.pushSummary, { secretHash: HASH_A, minuteTs: 0, ...HOT });
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(3);

  // After the cooldown it alerts again.
  vi.advanceTimersByTime(15 * 60_000);
  await t.mutation(api.agent.pushSummary, { secretHash: HASH_A, minuteTs: 0, ...HOT });
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(6);

  expect(await t.mutation(api.devices.clearAlerts, { token })).toBe(6);
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);
});

test("a backlog batch alerts on its newest minute only", async () => {
  const t = convexTest(schema, modules);
  const { token } = await userWithDevice(t, "aditya@example.com", HASH_A);
  const now = Date.now();
  await t.mutation(api.agent.pushSummaries, {
    secretHash: HASH_A,
    sentAt: now,
    summaries: [
      { minuteTs: now - 3 * 60_000, ...HOT },
      { minuteTs: now - 2 * 60_000, ...HOT },
      { minuteTs: now - 60_000, ...CALM },
    ],
  });
  expect(await t.query(api.devices.getMyAlerts, { token })).toHaveLength(0);
});

test("push tokens are stored once and move with the account that registers them", async () => {
  const t = convexTest(schema, modules);
  const a = await userWithDevice(t, "aditya@example.com", HASH_A);
  const b = await userWithDevice(t, "tirth@example.com", HASH_B);
  const fcmToken = "f".repeat(140);

  await t.mutation(api.devices.registerPushToken, { token: a.token, fcmToken, phoneModel: "Pixel 8" });
  await t.mutation(api.devices.registerPushToken, { token: a.token, fcmToken: ` ${fcmToken} ` });
  let rows = await t.run((ctx) => ctx.db.query("pushTokens").collect());
  expect(rows).toHaveLength(1);

  await t.mutation(api.devices.registerPushToken, { token: b.token, fcmToken });
  rows = await t.run((ctx) => ctx.db.query("pushTokens").collect());
  expect(rows).toHaveLength(1);
  const bUser = await t.run((ctx) => ctx.db.query("users").filter((q) => q.eq(q.field("email"), "tirth@example.com")).unique());
  expect(rows[0].userId).toBe(bUser!._id);

  await expect(t.mutation(api.devices.registerPushToken, { token: a.token, fcmToken: "short" })).rejects.toThrow(
    /invalid push token/,
  );
});

test("deleting a device or the account removes its alerts, thresholds and push tokens", async () => {
  const t = convexTest(schema, modules);
  const { token, deviceId } = await userWithDevice(t, "aditya@example.com", HASH_A);
  await t.mutation(api.agent.pushSummary, { secretHash: HASH_A, minuteTs: 0, ...HOT });
  await t.mutation(api.devices.updateThresholds, { token, cpuTempC: 80, gpuTempC: 75, ramPct: 85 });
  await t.mutation(api.devices.registerPushToken, { token, fcmToken: "f".repeat(140) });

  await t.mutation(api.devices.deleteDevice, { token, deviceId });
  await t.finishAllScheduledFunctions(vi.runAllTimers);
  expect(await t.run((ctx) => ctx.db.query("alerts").collect())).toHaveLength(0);
  expect(await t.query(api.devices.getMyDevices, { token })).toHaveLength(0);

  await t.mutation(api.auth.deleteAccount, { token });
  expect(await t.run((ctx) => ctx.db.query("thresholds").collect())).toHaveLength(0);
  expect(await t.run((ctx) => ctx.db.query("pushTokens").collect())).toHaveLength(0);
});

test("the daily cleanup deletes alerts older than 7 days", async () => {
  const t = convexTest(schema, modules);
  const { token } = await userWithDevice(t, "aditya@example.com", HASH_A);
  await t.mutation(api.agent.pushSummary, { secretHash: HASH_A, minuteTs: 0, ...HOT });
  vi.advanceTimersByTime(8 * 24 * 60 * 60 * 1000);
  const res = await t.mutation(internal.cleanup.deleteExpired, {});
  expect(res.alerts).toBe(3);
  expect(await t.run((ctx) => ctx.db.query("alerts").collect())).toHaveLength(0);
  void token;
});
