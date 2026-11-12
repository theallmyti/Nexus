/// <reference types="vite/client" />
import { convexTest } from "convex-test";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import { api, internal } from "./_generated/api";
import schema from "./schema";
import { sentEmails } from "../vitest.setup";

const modules = import.meta.glob("./**/*.ts");

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-13T10:00:00Z"));
});
afterEach(() => vi.useRealTimers());

const EMAIL = "aditya@example.com";
const signUp = (t: ReturnType<typeof convexTest>) =>
  t.action(api.auth.signUp, { turnstileToken: "test-token", email: EMAIL, password: "old password" });
const codeFromEmail = () => /(\d{6})/.exec(sentEmails.at(-1)!.subject)![1];

test("reset by emailed code: new password works, old one and old sessions don't", async () => {
  const t = convexTest(schema, modules);
  const { token: oldSession } = await signUp(t);

  await t.action(api.auth.requestPasswordReset, { email: " Aditya@Example.com " });
  expect(sentEmails).toHaveLength(1);
  expect(sentEmails[0].to).toBe(EMAIL);
  const code = codeFromEmail();
  expect(sentEmails[0].text).toContain(code);
  // Only the hash is stored.
  const row = (await t.run((ctx) => ctx.db.query("passwordResets").collect()))[0];
  expect(row.codeHash).not.toContain(code);

  const { token } = await t.action(api.auth.resetPassword, { email: EMAIL, code, newPassword: "new password 1" });
  expect(await t.query(api.auth.me, { token })).toEqual({ email: EMAIL });
  expect(await t.query(api.auth.me, { token: oldSession })).toBeNull();
  await expect(t.action(api.auth.signIn, { email: EMAIL, password: "old password" })).rejects.toThrow(/Wrong email or password/);
  await t.action(api.auth.signIn, { email: EMAIL, password: "new password 1" });

  // A code works once.
  await expect(t.action(api.auth.resetPassword, { email: EMAIL, code, newPassword: "another one 2" })).rejects.toThrow(
    /wrong or has expired/,
  );
});

test("unknown emails get the same answer and no email", async () => {
  const t = convexTest(schema, modules);
  expect(await t.action(api.auth.requestPasswordReset, { email: "nobody@example.com" })).toEqual({ sent: true });
  expect(sentEmails).toHaveLength(0);
});

test("codes expire after 15 minutes and lock after 5 wrong tries", async () => {
  const t = convexTest(schema, modules);
  await signUp(t);
  await t.action(api.auth.requestPasswordReset, { email: EMAIL });
  const code = codeFromEmail();
  const wrong = code === "000000" ? "111111" : "000000";
  for (let i = 0; i < 5; i++) {
    await expect(t.action(api.auth.resetPassword, { email: EMAIL, code: wrong, newPassword: "new password 1" })).rejects.toThrow(
      /wrong or has expired/,
    );
  }
  // Locked: even the right code fails now.
  await expect(t.action(api.auth.resetPassword, { email: EMAIL, code, newPassword: "new password 1" })).rejects.toThrow(
    /wrong or has expired/,
  );

  vi.advanceTimersByTime(60 * 60 * 1000); // new hour: rate limit window passed
  await t.action(api.auth.requestPasswordReset, { email: EMAIL });
  const fresh = codeFromEmail();
  vi.advanceTimersByTime(15 * 60 * 1000 + 1);
  await expect(t.action(api.auth.resetPassword, { email: EMAIL, code: fresh, newPassword: "new password 1" })).rejects.toThrow(
    /wrong or has expired/,
  );
  // The daily cleanup removes expired codes.
  vi.advanceTimersByTime(60 * 60 * 1000);
  expect((await t.mutation(internal.cleanup.deleteExpired, {})).resets).toBe(1);
});

test("at most 3 reset emails per address per hour; misconfigured email fails clearly", async () => {
  const t = convexTest(schema, modules);
  await signUp(t);
  for (let i = 0; i < 3; i++) await t.action(api.auth.requestPasswordReset, { email: EMAIL });
  await expect(t.action(api.auth.requestPasswordReset, { email: EMAIL })).rejects.toThrow(/Too many reset emails/);
  expect(sentEmails).toHaveLength(3);

  delete process.env.CF_EMAIL_API_TOKEN;
  await expect(t.action(api.auth.requestPasswordReset, { email: "x@example.com" })).rejects.toThrow(/isn't set up/);
});
