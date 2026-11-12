/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api } from "./_generated/api";
import schema from "./schema";

const modules = import.meta.glob("./**/*.ts");

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-13T10:00:00Z"));
});
afterEach(() => vi.useRealTimers());

const EMAIL = "aditya@example.com";
const signUp = (t: ReturnType<typeof convexTest>, name?: string) =>
  t.action(api.auth.signUp, { turnstileToken: "test-token", email: EMAIL, password: "old password", name });

test("profile: name from sign-up, editable, cleaned and required", async () => {
  const t = convexTest(schema, modules);
  const { token } = await signUp(t, "  Aditya  ");
  expect(await t.query(api.auth.profile, { token })).toMatchObject({ email: EMAIL, name: "Aditya", deviceCount: 0 });

  await t.mutation(api.auth.updateProfile, { token, name: "Adi\u0007 P." });
  expect((await t.query(api.auth.profile, { token })).name).toBe("Adi P.");
  await expect(t.mutation(api.auth.updateProfile, { token, name: "   " })).rejects.toThrow(/Enter a name/);
  await expect(t.query(api.auth.profile, { token: "nope" })).rejects.toThrow(/signed out/);
});

test("change password needs the current one, keeps this session, signs out the others", async () => {
  const t = convexTest(schema, modules);
  const { token: phone } = await signUp(t);
  const { token: laptop } = await t.action(api.auth.signIn, { email: EMAIL, password: "old password" });

  await expect(
    t.action(api.auth.changePassword, { token: phone, currentPassword: "wrong one", newPassword: "new password 1" }),
  ).rejects.toThrow(/current password is wrong/);
  await expect(
    t.action(api.auth.changePassword, { token: phone, currentPassword: "old password", newPassword: "short" }),
  ).rejects.toThrow(/at least 8/);

  await t.action(api.auth.changePassword, { token: phone, currentPassword: "old password", newPassword: "new password 1" });
  expect(await t.query(api.auth.me, { token: phone })).toEqual({ email: EMAIL });
  expect(await t.query(api.auth.me, { token: laptop })).toBeNull();
  await t.action(api.auth.signIn, { email: EMAIL, password: "new password 1" });
});

test("wrong current passwords are rate limited like sign-in", async () => {
  const t = convexTest(schema, modules);
  const { token } = await signUp(t);
  for (let i = 0; i < 5; i++) {
    await expect(
      t.action(api.auth.changePassword, { token, currentPassword: `bad ${i}`, newPassword: "new password 1" }),
    ).rejects.toThrow(/current password is wrong/);
  }
  await expect(
    t.action(api.auth.changePassword, { token, currentPassword: "old password", newPassword: "new password 1" }),
  ).rejects.toThrow(/Too many attempts/);
});

test("sign out everywhere ends every session, including this one", async () => {
  const t = convexTest(schema, modules);
  const { token: a } = await signUp(t);
  const { token: b } = await t.action(api.auth.signIn, { email: EMAIL, password: "old password" });
  expect(await t.mutation(api.auth.signOutEverywhere, { token: a })).toEqual({ count: 2 });
  expect(await t.query(api.auth.me, { token: a })).toBeNull();
  expect(await t.query(api.auth.me, { token: b })).toBeNull();
});
