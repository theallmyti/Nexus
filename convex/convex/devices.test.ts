/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api } from "./_generated/api";
import schema from "./schema";
import { Id } from "./_generated/dataModel";

import * as devicesModule from "./devices";

const modules = import.meta.glob("./**/*.ts");

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-12T00:00:00Z"));
});
afterEach(() => vi.useRealTimers());

async function userWithDevice(t: ReturnType<typeof convexTest>, email: string, secretHash: string) {
  const { token } = await t.action(api.auth.signUp, { email, password: "test-password-123" });
  const { code } = await t.mutation(api.pairing.start, {
    secretHash,
    name: email.split("@")[0],
    os: "linux",
  });
  const res = await t.mutation(api.devices.confirmPairing, { token, code });
  const device = await t.run(async (ctx) => await ctx.db.get(res.deviceId as Id<"devices">));
  return { token, deviceId: res.deviceId as Id<"devices">, userId: device!.userId };
}

test("Baseline & Happy Path: authenticated user manages their own devices", async () => {
  const t = convexTest(schema, modules);
  const user = await userWithDevice(t, "userA@example.com", "a".repeat(64));

  // 1. getMyDevices
  const myDevices = await t.query(api.devices.getMyDevices, { token: user.token });
  expect(myDevices.length).toBe(1);
  expect(myDevices[0]._id).toBe(user.deviceId);

  // 2. getDevice
  const device = await t.query(api.devices.getDevice, { token: user.token, deviceId: user.deviceId });
  expect(device).toBeDefined();
  expect(device?._id).toBe(user.deviceId);

  // 3. updateDeviceName
  await t.mutation(api.devices.updateDeviceName, { token: user.token, deviceId: user.deviceId, name: "homeserver-renamed" });
  const updated = await t.query(api.devices.getDevice, { token: user.token, deviceId: user.deviceId });
  expect(updated?.name).toBe("homeserver-renamed");

  // 4. setViewer
  const viewerRes = await t.mutation(api.devices.setViewer, { token: user.token, deviceId: user.deviceId });
  expect(viewerRes.viewerUntil).toBeGreaterThan(Date.now());

  // 5. updateThresholds & getThresholds
  await t.mutation(api.devices.updateThresholds, { token: user.token, cpuTempC: 85, ramPct: 90 });
  const thresholds = await t.query(api.devices.getThresholds, { token: user.token });
  expect(thresholds?.cpuTempC).toBe(85);
  expect(thresholds?.ramPct).toBe(90);

  // 6. registerPushToken
  const pushRes = await t.mutation(api.devices.registerPushToken, { token: user.token, fcmToken: "fcm_token_123" });
  expect(pushRes.success).toBe(true);

  // 7. deleteDevice
  await t.mutation(api.devices.deleteDevice, { token: user.token, deviceId: user.deviceId });
  const remaining = await t.query(api.devices.getMyDevices, { token: user.token });
  expect(remaining.length).toBe(0);
});

test("Security (Tier 7): Unauthenticated access with invalid or empty token is rejected", async () => {
  const t = convexTest(schema, modules);
  const user = await userWithDevice(t, "userA@example.com", "a".repeat(64));

  // Invalid token
  await expect(t.query(api.devices.getMyDevices, { token: "bad_token" })).rejects.toThrow(/signed out/);
  await expect(t.query(api.devices.getDevice, { token: "bad_token", deviceId: user.deviceId })).rejects.toThrow(/signed out/);
  await expect(t.query(api.devices.getDeviceLatest, { token: "bad_token", deviceId: user.deviceId })).rejects.toThrow(/signed out/);
  await expect(t.mutation(api.devices.deleteDevice, { token: "bad_token", deviceId: user.deviceId })).rejects.toThrow(/signed out/);
  await expect(t.query(api.devices.getMyAlerts, { token: "bad_token" })).rejects.toThrow(/signed out/);

  // Empty / whitespace token
  await expect(t.query(api.devices.getMyDevices, { token: "" })).rejects.toThrow(/signed out/);
  await expect(t.query(api.devices.getMyDevices, { token: "   " })).rejects.toThrow(/signed out/);
});

test("Security (Tier 7): IDOR Prevention — User B cannot read or modify User A's devices", async () => {
  const t = convexTest(schema, modules);
  const userA = await userWithDevice(t, "userA@example.com", "a".repeat(64));
  const userB = await userWithDevice(t, "userB@example.com", "b".repeat(64));

  // 1. getDevice IDOR
  await expect(
    t.query(api.devices.getDevice, { token: userB.token, deviceId: userA.deviceId })
  ).rejects.toThrow(/Unauthorized/);

  // 2. getDeviceLatest IDOR
  await expect(
    t.query(api.devices.getDeviceLatest, { token: userB.token, deviceId: userA.deviceId })
  ).rejects.toThrow(/Unauthorized/);

  // 3. updateDeviceName IDOR
  await expect(
    t.mutation(api.devices.updateDeviceName, { token: userB.token, deviceId: userA.deviceId, name: "hacked" })
  ).rejects.toThrow(/Unauthorized/);

  // 4. setViewer IDOR
  await expect(
    t.mutation(api.devices.setViewer, { token: userB.token, deviceId: userA.deviceId })
  ).rejects.toThrow(/Unauthorized/);

  // 5. deleteDevice IDOR
  await expect(
    t.mutation(api.devices.deleteDevice, { token: userB.token, deviceId: userA.deviceId })
  ).rejects.toThrow(/Unauthorized/);

  // 6. getDeviceSummaries IDOR
  await expect(
    t.query(api.devices.getDeviceSummaries, { token: userB.token, deviceId: userA.deviceId })
  ).rejects.toThrow(/Unauthorized/);

  // Confirm User A's device was NOT deleted or modified by User B's attempts
  const checkDevice = await t.query(api.devices.getDevice, { token: userA.token, deviceId: userA.deviceId });
  expect(checkDevice).toBeDefined();
  expect(checkDevice?.name).toBe("userA");
});

test("Security (Tier 7): Cross-Tenant Alerts & Threshold Isolation", async () => {
  const t = convexTest(schema, modules);
  const userA = await userWithDevice(t, "userA@example.com", "a".repeat(64));
  const userB = await userWithDevice(t, "userB@example.com", "b".repeat(64));

  // User A sets thresholds
  await t.mutation(api.devices.updateThresholds, { token: userA.token, cpuTempC: 75, ramPct: 80 });

  // User B sets different thresholds
  await t.mutation(api.devices.updateThresholds, { token: userB.token, cpuTempC: 95, ramPct: 95 });

  // Verify User A still has their own thresholds
  const threshA = await t.query(api.devices.getThresholds, { token: userA.token });
  expect(threshA?.cpuTempC).toBe(75);
  expect(threshA?.ramPct).toBe(80);

  // Verify User B has their own thresholds
  const threshB = await t.query(api.devices.getThresholds, { token: userB.token });
  expect(threshB?.cpuTempC).toBe(95);
  expect(threshB?.ramPct).toBe(95);

  // Insert alerts directly for User A
  await t.run(async (ctx) => {
    await ctx.db.insert("alerts", {
      userId: userA.userId,
      deviceId: userA.deviceId,
      type: "cpuTemp",
      value: 99,
      threshold: 90,
      createdAt: Date.now(),
      pushed: false,
    });
  });

  // User B queries alerts -> receives 0 alerts
  const userBAlerts = await t.query(api.devices.getMyAlerts, { token: userB.token });
  expect(userBAlerts.length).toBe(0);

  // User B clears alerts -> does not delete User A's alerts
  await t.mutation(api.devices.clearAlerts, { token: userB.token });
  const userAAlerts = await t.query(api.devices.getMyAlerts, { token: userA.token });
  expect(userAAlerts.length).toBe(1);
});

test("Security (Tier 7): Backdoor endpoints are completely removed from module exports", () => {
  expect((devicesModule as any).getAllDevicesAdmin).toBeUndefined();
  expect((devicesModule as any).assignDeviceToEmail).toBeUndefined();
  expect((devicesModule as any).enrollDeviceForUser).toBeUndefined();
  expect((devicesModule as any).resolveUserId).toBeUndefined();
  expect((devicesModule as any).resolveOrCreateUserId).toBeUndefined();
});

test("Input Validation (Tier 1 & 3): updateDeviceName rejects empty/whitespace names", async () => {
  const t = convexTest(schema, modules);
  const user = await userWithDevice(t, "userA@example.com", "a".repeat(64));

  await expect(
    t.mutation(api.devices.updateDeviceName, { token: user.token, deviceId: user.deviceId, name: "" })
  ).rejects.toThrow(/cannot be empty/);

  await expect(
    t.mutation(api.devices.updateDeviceName, { token: user.token, deviceId: user.deviceId, name: "    " })
  ).rejects.toThrow(/cannot be empty/);
});

test("Temporal & Lifecycle (Tier 3 & 8): Expired and already confirmed pairing codes are rejected", async () => {
  const t = convexTest(schema, modules);
  const user = await userWithDevice(t, "userA@example.com", "a".repeat(64));

  // Expired code test
  const { code: expiredCode } = await t.mutation(api.pairing.start, {
    secretHash: "c".repeat(64),
    name: "expired-device",
    os: "linux",
  });
  // Advance time past the 10-minute expiry
  vi.advanceTimersByTime(11 * 60 * 1000);

  await expect(
    t.mutation(api.devices.confirmPairing, { token: user.token, code: expiredCode })
  ).rejects.toThrow(/expired/);

  // Reset time for new test
  vi.setSystemTime(new Date("2026-11-12T01:00:00Z"));
  const { code: newCode } = await t.mutation(api.pairing.start, {
    secretHash: "d".repeat(64),
    name: "device-two",
    os: "linux",
  });
  await t.mutation(api.devices.confirmPairing, { token: user.token, code: newCode });

  // Replay of already confirmed code
  await expect(
    t.mutation(api.devices.confirmPairing, { token: user.token, code: newCode })
  ).rejects.toThrow(/already confirmed/);
});

test("Rate Limiting: devices.confirmPairing throttles after consecutive wrong pairing attempts", async () => {
  const t = convexTest(schema, modules);
  const { token } = await t.action(api.auth.signUp, { email: "rate-limit-test@example.com", password: "test-password-123" });

  // Make 10 failed guesses (each returns null and records rate event)
  for (let i = 0; i < 10; i++) {
    const res = await t.mutation(api.devices.confirmPairing, { token, code: `WRONG${i}` });
    expect(res).toBeNull();
  }

  // The 11th attempt should trigger rate limiting
  await expect(
    t.mutation(api.devices.confirmPairing, { token, code: "ANOTHER_TRY" })
  ).rejects.toThrow(/Too many wrong pairing codes/);
});

test("Custom Per-Device Thresholds: sets, retrieves with fallback, and resets", async () => {
  const t = convexTest(schema, modules);
  const user = await userWithDevice(t, "device-thresh@example.com", "e".repeat(64));

  // 1. Set global threshold: CPU 90, RAM 85
  await t.mutation(api.devices.updateThresholds, {
    token: user.token,
    cpuTempC: 90,
    ramPct: 85,
  });

  // Verify global threshold
  const globalThresh = await t.query(api.devices.getThresholds, { token: user.token });
  expect(globalThresh?.cpuTempC).toBe(90);
  expect(globalThresh?.ramPct).toBe(85);

  // Before custom threshold, device falls back to global
  const fallback = await t.query(api.devices.getThresholds, {
    token: user.token,
    deviceId: user.deviceId,
  });
  expect(fallback?.cpuTempC).toBe(90);
  expect(fallback?.isCustom).toBe(false);

  // 2. Set custom threshold for device: CPU 75, RAM 70
  await t.mutation(api.devices.updateThresholds, {
    token: user.token,
    deviceId: user.deviceId,
    cpuTempC: 75,
    ramPct: 70,
  });

  // Verify device gets custom thresholds
  const custom = await t.query(api.devices.getThresholds, {
    token: user.token,
    deviceId: user.deviceId,
  });
  expect(custom?.cpuTempC).toBe(75);
  expect(custom?.ramPct).toBe(70);
  expect(custom?.isCustom).toBe(true);

  // Global threshold remains unaffected
  const globalAfter = await t.query(api.devices.getThresholds, { token: user.token });
  expect(globalAfter?.cpuTempC).toBe(90);

  // 3. Reset custom threshold for device
  await t.mutation(api.devices.resetDeviceThresholds, {
    token: user.token,
    deviceId: user.deviceId,
  });

  // Now device falls back to global again
  const reverted = await t.query(api.devices.getThresholds, {
    token: user.token,
    deviceId: user.deviceId,
  });
  expect(reverted?.cpuTempC).toBe(90);
  expect(reverted?.isCustom).toBe(false);
});
