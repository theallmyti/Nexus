// Internal account steps used by auth.ts actions (not callable from clients).
import { internalMutation, internalQuery } from "./_generated/server";
import { v, ConvexError } from "convex/values";
import { LIMITS, isLimited, recordEvent } from "./rateLimit";

// FR-04: 5 failed sign-ins per 15 minutes per email.
export const ATTEMPT_WINDOW_MS = 15 * 60 * 1000;
export const MAX_ATTEMPTS = 5;

export const byEmail = internalQuery({
  args: { email: v.string() },
  handler: async (ctx, args) =>
    await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .unique(),
});

export const create = internalMutation({
  args: { email: v.string(), passwordHash: v.string(), name: v.optional(v.string()) },
  handler: async (ctx, args) => {
    const existing = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .unique();
    if (existing) {
      throw new ConvexError("An account with this email already exists. Sign in instead.");
    }
    return await ctx.db.insert("users", {
      email: args.email,
      passwordHash: args.passwordHash,
      createdAt: Date.now(),
      setupDone: false,
      name: args.name,
    });
  },
});

export const startSession = internalMutation({
  args: { userId: v.id("users"), tokenHash: v.string() },
  handler: async (ctx, args) => {
    await ctx.db.insert("sessions", { ...args, lastUsedAt: Date.now() });
  },
});

export const recentAttempts = internalQuery({
  args: { email: v.string() },
  handler: async (ctx, args) => {
    const since = Date.now() - ATTEMPT_WINDOW_MS;
    const rows = await ctx.db
      .query("loginAttempts")
      .withIndex("by_email_at", (q) => q.eq("email", args.email).gt("at", since))
      .take(MAX_ATTEMPTS);
    return rows.length;
  },
});

export const recordAttempt = internalMutation({
  args: { email: v.string() },
  handler: async (ctx, args) => {
    await ctx.db.insert("loginAttempts", { email: args.email, at: Date.now() });
  },
});

export const clearAttempts = internalMutation({
  args: { email: v.string() },
  handler: async (ctx, args) => {
    const rows = await ctx.db
      .query("loginAttempts")
      .withIndex("by_email_at", (q) => q.eq("email", args.email))
      .collect();
    for (const r of rows) await ctx.db.delete(r._id);
  },
});

// --- Password reset (auth.requestPasswordReset / auth.resetPassword) ---

export const RESET_CODE_TTL_MS = 15 * 60 * 1000;
export const RESET_MAX_ATTEMPTS = 5;
const RESET_FAILED = "That code is wrong or has expired. Request a new one.";

// Stores a new code for the account (replacing any open one). Returns false when the address has
// asked for too many codes; null when there is no account (the caller answers the same either way).
export const startPasswordReset = internalMutation({
  args: { email: v.string(), codeHash: v.string() },
  handler: async (ctx, args) => {
    const key = `reset:${args.email}`;
    if (await isLimited(ctx, key, LIMITS.resetPerEmail)) return false;
    await recordEvent(ctx, key);
    const user = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .unique();
    if (!user) return null;
    for (const old of await ctx.db
      .query("passwordResets")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect()) {
      await ctx.db.delete(old._id);
    }
    await ctx.db.insert("passwordResets", {
      userId: user._id,
      codeHash: args.codeHash,
      expiresAt: Date.now() + RESET_CODE_TTL_MS,
      attempts: 0,
    });
    return true;
  },
});

// Checks the code and, if right, sets the new password, signs out every session and starts a new
// one. A wrong code counts an attempt (5 per code); the error never says which part was wrong.
export const finishPasswordReset = internalMutation({
  args: { email: v.string(), codeHash: v.string(), passwordHash: v.string(), newTokenHash: v.string() },
  handler: async (ctx, args) => {
    const user = await ctx.db
      .query("users")
      .withIndex("by_email", (q) => q.eq("email", args.email))
      .unique();
    const reset = user
      ? await ctx.db
          .query("passwordResets")
          .withIndex("by_user", (q) => q.eq("userId", user._id))
          .first()
      : null;
    if (!user || !reset || Date.now() > reset.expiresAt || reset.attempts >= RESET_MAX_ATTEMPTS) {
      return { ok: false as const, error: RESET_FAILED };
    }
    if (reset.codeHash !== args.codeHash) {
      await ctx.db.patch(reset._id, { attempts: reset.attempts + 1 });
      return { ok: false as const, error: RESET_FAILED };
    }
    await ctx.db.delete(reset._id);
    await ctx.db.patch(user._id, { passwordHash: args.passwordHash });
    for (const s of await ctx.db
      .query("sessions")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect()) {
      await ctx.db.delete(s._id);
    }
    for (const a of await ctx.db
      .query("loginAttempts")
      .withIndex("by_email_at", (q) => q.eq("email", args.email))
      .collect()) {
      await ctx.db.delete(a._id);
    }
    await ctx.db.insert("sessions", { userId: user._id, tokenHash: args.newTokenHash, lastUsedAt: Date.now() });
    return { ok: true as const };
  },
});

// --- Account & Security (auth.changePassword) ---

// The signed-in user's password hash, to check the current password in the action.
export const passwordHashForSession = internalQuery({
  args: { tokenHash: v.string() },
  handler: async (ctx, args) => {
    const session = await ctx.db
      .query("sessions")
      .withIndex("by_tokenHash", (q) => q.eq("tokenHash", args.tokenHash))
      .unique();
    if (!session) return null;
    const user = await ctx.db.get(session.userId);
    return user ? { userId: user._id, email: user.email, passwordHash: user.passwordHash } : null;
  },
});

// New password; every other session is signed out, the one used for the change stays.
export const setPasswordKeepSession = internalMutation({
  args: { userId: v.id("users"), passwordHash: v.string(), keepTokenHash: v.string() },
  handler: async (ctx, args) => {
    await ctx.db.patch(args.userId, { passwordHash: args.passwordHash });
    for (const s of await ctx.db
      .query("sessions")
      .withIndex("by_user", (q) => q.eq("userId", args.userId))
      .collect()) {
      if (s.tokenHash !== args.keepTokenHash) await ctx.db.delete(s._id);
    }
  },
});
