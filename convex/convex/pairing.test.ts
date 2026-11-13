/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api } from "./_generated/api";
import schema from "./schema";

const modules = import.meta.glob("./**/*.ts");
const HASH = "c".repeat(64);
const DEVICE = { secretHash: HASH, name: "test-pc", os: "windows" };

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-04T10:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

async function signedIn(t: ReturnType<typeof convexTest>, email = "aditya@example.com") {
  const { token } = await t.action(api.auth.signUp, { turnstileToken: "test-token", email, password: "password one" });
  return token;
}

test("start returns a readable 6-character code valid for 10 minutes", async () => {
  const t = convexTest(schema, modules);
  const { code, expiresAt } = await t.mutation(api.pairing.start, DEVICE);
  expect(code).toMatch(/^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$/);
  expect(expiresAt).toBe(Date.now() + 10 * 60 * 1000);
  expect(await t.query(api.pairing.status, { code, secretHash: HASH })).toBe("pending");
});

test("running pair again replaces the previous code", async () => {
  const t = convexTest(schema, modules);
  const first = await t.mutation(api.pairing.start, DEVICE);
  const second = await t.mutation(api.pairing.start, DEVICE);
  expect(await t.query(api.pairing.status, { code: first.code, secretHash: HASH })).toBe("unknown");
  expect(await t.query(api.pairing.status, { code: second.code, secretHash: HASH })).toBe("pending");
});

test("the signed-in user who confirms becomes the owner; then hello reports owned", async () => {
  const t = convexTest(schema, modules);
  const token = await signedIn(t);
  await expect(t.mutation(api.agent.hello, { secretHash: HASH })).rejects.toThrow(/not paired/);

  const { code } = await t.mutation(api.pairing.start, DEVICE);
  const info = await t.mutation(api.dashboard.checkPairingCode, { token, code: code.toLowerCase() });
  expect(info).toEqual({ name: "test-pc", os: "windows", state: "pending" });

  const { deviceId } = (await t.mutation(api.dashboard.confirmPairing, { token, code }))!;
  expect(await t.query(api.pairing.status, { code, secretHash: HASH })).toBe("confirmed");
  const device = await t.run((ctx) => ctx.db.get(deviceId));
  const [user] = await t.run((ctx) => ctx.db.query("users").collect());
  expect(device?.userId).toBe(user._id);
  expect(device?.isFake).toBe(false);

  expect(await t.mutation(api.agent.hello, { secretHash: HASH, agentVersion: "0.1.0" })).toMatchObject({
    deviceId,
    owned: true,
  });
});

test("a device paired before accounts (no owner) is claimed by pairing it again", async () => {
  const t = convexTest(schema, modules);
  const token = await signedIn(t);
  const oldId = await t.run((ctx) =>
    ctx.db.insert("devices", { name: "old-pc", secretHash: HASH, lastSeenAt: 0, isFake: false }),
  );
  expect(await t.mutation(api.agent.hello, { secretHash: HASH })).toMatchObject({ deviceId: oldId, owned: false });

  const { code } = await t.mutation(api.pairing.start, DEVICE);
  const { deviceId } = (await t.mutation(api.dashboard.confirmPairing, { token, code }))!;
  expect(deviceId).toBe(oldId); // same device, history kept
  expect(await t.mutation(api.agent.hello, { secretHash: HASH })).toMatchObject({ deviceId: oldId, owned: true });
});

test("an expired code cannot be confirmed", async () => {
  const t = convexTest(schema, modules);
  const token = await signedIn(t);
  const { code } = await t.mutation(api.pairing.start, DEVICE);
  // The 10 minutes have passed.
  const [request] = await t.run((ctx) => ctx.db.query("pairingRequests").collect());
  await t.run((ctx) => ctx.db.patch(request._id, { expiresAt: Date.now() - 1 }));
  expect(await t.query(api.pairing.status, { code, secretHash: HASH })).toBe("expired");
  await expect(t.mutation(api.dashboard.confirmPairing, { token, code })).rejects.toThrow(/not found or expired/);
});

test("confirming needs a signed-in user, and status needs the right device", async () => {
  const t = convexTest(schema, modules);
  const { code } = await t.mutation(api.pairing.start, DEVICE);
  await expect(t.mutation(api.dashboard.confirmPairing, { token: "f".repeat(64), code })).rejects.toThrow(
    /signed out/,
  );
  expect(await t.query(api.pairing.status, { code, secretHash: "d".repeat(64) })).toBe("unknown");
  await expect(t.mutation(api.pairing.start, { ...DEVICE, secretHash: "not-a-hash" })).rejects.toThrow(
    /invalid device key/,
  );
});

test("pairing someone else's computer shares it: view only, owner unchanged", async () => {
  const t = convexTest(schema, modules);
  const ownerToken = await signedIn(t);
  const otherToken = (await t.action(api.auth.signUp, { turnstileToken: "test-token", email: "family@example.com", password: "password two" })).token;

  const { code: ownerCode } = await t.mutation(api.pairing.start, DEVICE);
  const { deviceId } = (await t.mutation(api.dashboard.confirmPairing, { token: ownerToken, code: ownerCode }))!;

  // A second account pairs the same computer (it needs a code from the computer itself).
  const { code } = await t.mutation(api.pairing.start, DEVICE);
  const res = await t.mutation(api.dashboard.confirmPairing, { token: otherToken, code });
  expect(res).toMatchObject({ deviceId, shared: true });

  // Both see it; ownership did not move.
  const owner = await t.query(api.dashboard.overview, { token: ownerToken });
  const other = await t.query(api.dashboard.overview, { token: otherToken });
  expect(owner.devices).toEqual([expect.objectContaining({ id: deviceId, shared: false })]);
  expect(other.devices).toEqual([expect.objectContaining({ id: deviceId, shared: true })]);
  const row = await t.run((ctx) => ctx.db.get(deviceId));
  expect(row!.userId).not.toBeUndefined();

  // The shared account can't rename it, and "remove" only takes it off their own list.
  await expect(t.mutation(api.dashboard.renameDevice, { token: otherToken, deviceId, name: "mine now" })).rejects.toThrow();
  await t.mutation(api.dashboard.removeDevice, { token: otherToken, deviceId });
  expect((await t.query(api.dashboard.overview, { token: otherToken })).devices).toEqual([]);
  expect((await t.query(api.dashboard.overview, { token: ownerToken })).devices).toHaveLength(1);
});

test("owner sees who has access and can remove them; secret hash never leaves the server", async () => {
  const t = convexTest(schema, modules);
  const ownerToken = await signedIn(t);
  const otherToken = (await t.action(api.auth.signUp, { turnstileToken: "test-token", email: "family@example.com", password: "password two" })).token;
  const { code: c1 } = await t.mutation(api.pairing.start, DEVICE);
  const { deviceId } = (await t.mutation(api.dashboard.confirmPairing, { token: ownerToken, code: c1 }))!;
  const { code: c2 } = await t.mutation(api.pairing.start, DEVICE);
  await t.mutation(api.devices.confirmPairing, { token: otherToken, code: c2 });

  const shares = await t.query(api.devices.getDeviceShares, { token: ownerToken, deviceId });
  expect(shares).toEqual([expect.objectContaining({ email: "family@example.com" })]);
  // Only the owner may list access.
  await expect(t.query(api.devices.getDeviceShares, { token: otherToken, deviceId })).rejects.toThrow();

  // The device record sent to apps has no secret hash (it is the agent's credential).
  const forOther = await t.query(api.devices.getMyDevices, { token: otherToken });
  expect(forOther[0]).not.toHaveProperty("secretHash");
  expect(forOther[0]).toMatchObject({ shared: true });

  await t.mutation(api.devices.removeDeviceShare, { token: ownerToken, deviceId, userId: shares[0].userId });
  expect(await t.query(api.devices.getMyDevices, { token: otherToken })).toEqual([]);
});

