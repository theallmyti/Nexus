/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api, internal } from "./_generated/api";
import schema from "./schema";
import { LIMITS } from "./rateLimit";

const modules = import.meta.glob("./**/*.ts");
const DEVICE = { secretHash: "c".repeat(64), name: "test-pc", os: "windows" };

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-04T10:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

async function addEvents(t: ReturnType<typeof convexTest>, key: string, count: number, at = Date.now()) {
  await t.run(async (ctx) => {
    for (let i = 0; i < count; i++) await ctx.db.insert("rateEvents", { key, at });
  });
}

test("sign-up needs a valid Turnstile token for this site's sign-up form", async () => {
  const t = convexTest(schema, modules);
  const signUp = (turnstileToken: string, email: string) =>
    t.action(api.auth.signUp, { turnstileToken, email, password: "password one" });

  for (const bad of ["", "invalid", "wrong-action", "wrong-host", "localhost-token", "network-fail", "server-error"]) {
    await expect(signUp(bad, `${bad || "empty"}@example.com`)).rejects.toThrow(/Bot check failed/);
  }
  expect(await t.run((ctx) => ctx.db.query("users").collect())).toHaveLength(0);

  // A missing secret fails closed rather than letting everyone through.
  delete process.env.TURNSTILE_SECRET;
  await expect(signUp("test-token", "nosecret@example.com")).rejects.toThrow(/Bot check failed/);
  process.env.TURNSTILE_SECRET = "test-secret";

  await signUp("test-token", "new@example.com");
  expect(await t.run((ctx) => ctx.db.query("users").collect())).toHaveLength(1);
});

test("there is no shared sign-up cap: many accounts in an hour all succeed", async () => {
  const t = convexTest(schema, modules);
  for (let i = 0; i < 12; i++) {
    await t.action(api.auth.signUp, { turnstileToken: "test-token", email: `user${i}@example.com`, password: "password one" });
  }
  expect(await t.run((ctx) => ctx.db.query("users").collect())).toHaveLength(12);
});

test("one device gets at most 5 pairing codes per 10 minutes", async () => {
  const t = convexTest(schema, modules);
  for (let i = 0; i < LIMITS.pairPerDevice.max; i++) await t.mutation(api.pairing.start, DEVICE);
  await expect(t.mutation(api.pairing.start, DEVICE)).rejects.toThrow(/Too many pairing codes/);
  // Another device is not affected.
  await t.mutation(api.pairing.start, { ...DEVICE, secretHash: "d".repeat(64) });
});

test("a flood of pairing codes from random device keys does not block other devices", async () => {
  const t = convexTest(schema, modules);
  for (let i = 0; i < 100; i++) {
    const secretHash = i.toString(16).padStart(64, "0");
    await t.mutation(api.pairing.start, { secretHash, name: "flood", os: "linux" });
  }
  const { code } = await t.mutation(api.pairing.start, DEVICE);
  expect(code).toHaveLength(6);
});

test("10 wrong pairing codes block the account for 10 minutes, even with the right code", async () => {
  const t = convexTest(schema, modules);
  const { token } = await t.action(api.auth.signUp, { turnstileToken: "test-token", email: "guesser@example.com", password: "password one" });
  const { code } = await t.mutation(api.pairing.start, DEVICE);
  for (let i = 0; i < LIMITS.wrongCode.max; i++) {
    expect(await t.mutation(api.dashboard.checkPairingCode, { token, code: "ZZZZZ" + i })).toBeNull();
  }
  // The wrong guesses were stored (not rolled back with an error).
  const events = await t.run((ctx) => ctx.db.query("rateEvents").collect());
  expect(events.filter((e) => e.key.startsWith("wrongCode:"))).toHaveLength(LIMITS.wrongCode.max);

  await expect(t.mutation(api.dashboard.checkPairingCode, { token, code })).rejects.toThrow(/Too many wrong codes/);
  await expect(t.mutation(api.dashboard.confirmPairing, { token, code })).rejects.toThrow(/Too many wrong codes/);

  // Wrong codes in confirmPairing count too, and a free account can still pair.
  const other = await t.action(api.auth.signUp, { turnstileToken: "test-token", email: "owner@example.com", password: "password one" });
  expect(await t.mutation(api.dashboard.confirmPairing, { token: other.token, code: "ZZZZZZ" })).toBeNull();
  expect(await t.mutation(api.dashboard.confirmPairing, { token: other.token, code })).toMatchObject({ name: "test-pc" });
});

test("the daily cleanup deletes old limit rows and expired codes, and keeps current ones", async () => {
  const t = convexTest(schema, modules);
  const now = Date.now();
  const day = 24 * 3_600_000;
  await addEvents(t, "signup", 2, now - day - 1);
  await addEvents(t, "signup", 1, now);
  await t.run(async (ctx) => {
    await ctx.db.insert("loginAttempts", { email: "a@example.com", at: now - day - 1 });
    await ctx.db.insert("loginAttempts", { email: "a@example.com", at: now });
    const base = { secretHash: "c".repeat(64), name: "pc", os: "windows", status: "pending" as const };
    await ctx.db.insert("pairingRequests", { ...base, code: "AAAAAA", expiresAt: now - 3_600_001 });
    await ctx.db.insert("pairingRequests", { ...base, code: "BBBBBB", expiresAt: now + 60_000 });
    const userId = await ctx.db.insert("users", { email: "s@example.com", passwordHash: "h", createdAt: now, setupDone: true });
    await ctx.db.insert("sessions", { userId, tokenHash: "old_sess", lastUsedAt: now - 31 * day });
    await ctx.db.insert("sessions", { userId, tokenHash: "cur_sess", lastUsedAt: now });
  });

  expect(await t.mutation(internal.cleanup.deleteExpired, {})).toEqual({ events: 2, attempts: 1, codes: 1, sessions: 1, alerts: 0 });
  const codes = await t.run((ctx) => ctx.db.query("pairingRequests").collect());
  expect(codes.map((c) => c.code)).toEqual(["BBBBBB"]);
  expect(await t.run((ctx) => ctx.db.query("rateEvents").collect())).toHaveLength(1);
  expect(await t.run((ctx) => ctx.db.query("loginAttempts").collect())).toHaveLength(1);
  const remainingSessions = await t.run((ctx) => ctx.db.query("sessions").collect());
  expect(remainingSessions.map((s) => s.tokenHash)).toEqual(["cur_sess"]);
});
