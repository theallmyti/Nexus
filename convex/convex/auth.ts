// Accounts: sign up, sign in, sign out (FR-01–FR-04, docs/Flow.md §1, docs/Security.md §3–4).
// Passwords are hashed with scrypt in actions (CPU-heavy work does not belong in mutations).
// Clients keep only the returned session token; the backend stores its SHA-256.
import { action, mutation, query } from "./_generated/server";
import { internal } from "./_generated/api";
import { v, ConvexError } from "convex/values";
import { dummyHash, hashPassword, newToken, tokenHash, verifyPassword } from "./passwords";
import { requireUser, requireUserAndTouch, sessionUser } from "./model";
import { MAX_ATTEMPTS } from "./users";
import { verifyTurnstile } from "./turnstile";
import { EMAIL_NOT_CONFIGURED, emailConfigured, sendEmail } from "./email";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MIN_PASSWORD = 8; // FR-01
const MAX_PASSWORD = 256;
const MAX_EMAIL = 255;

function normalizeEmail(email: string) {
  return email.trim().toLowerCase();
}

// Display name: no control characters, 1–40 characters, or nothing.
function cleanName(name: string | undefined): string | undefined {
  const n = (name ?? "").replace(/[\u0000-\u001F\u007F-\u009F]/g, "").trim().slice(0, 40);
  return n.length > 0 ? n : undefined;
}

// Scripts are stopped by a Cloudflare Turnstile token (turnstile.ts), checked before anything else.
export const signUp = action({
  args: { email: v.string(), password: v.string(), turnstileToken: v.string(), name: v.optional(v.string()) },
  handler: async (ctx, args): Promise<{ token: string }> => {
    await verifyTurnstile(args.turnstileToken, "signup");
    const email = normalizeEmail(args.email);
    if (email.length > MAX_EMAIL || !EMAIL.test(email)) {
      throw new ConvexError("Enter a valid email address.");
    }
    if (args.password.length < MIN_PASSWORD) {
      throw new ConvexError(`Use at least ${MIN_PASSWORD} characters for the password.`);
    }
    if (args.password.length > MAX_PASSWORD) {
      throw new ConvexError(`Use at most ${MAX_PASSWORD} characters for the password.`);
    }
    const userId = await ctx.runMutation(internal.users.create, {
      email,
      passwordHash: hashPassword(args.password),
      name: cleanName(args.name),
    });
    const token = newToken();
    await ctx.runMutation(internal.users.startSession, { userId, tokenHash: tokenHash(token) });
    return { token };
  },
});

export const signIn = action({
  args: { email: v.string(), password: v.string() },
  handler: async (ctx, args): Promise<{ token: string }> => {
    const email = normalizeEmail(args.email);
    if (email.length > MAX_EMAIL || args.password.length > MAX_PASSWORD) {
      throw new ConvexError("Wrong email or password.");
    }
    if ((await ctx.runQuery(internal.users.recentAttempts, { email })) >= MAX_ATTEMPTS) {
      throw new ConvexError("Too many attempts. Try again in 15 minutes.");
    }
    const user = await ctx.runQuery(internal.users.byEmail, { email });
    // Same work and the same message for an unknown email and a wrong password.
    const ok = verifyPassword(args.password, user?.passwordHash ?? dummyHash());
    if (!user || !ok) {
      await ctx.runMutation(internal.users.recordAttempt, { email });
      throw new ConvexError("Wrong email or password.");
    }
    await ctx.runMutation(internal.users.clearAttempts, { email });
    const token = newToken();
    await ctx.runMutation(internal.users.startSession, {
      userId: user._id,
      tokenHash: tokenHash(token),
    });
    return { token };
  },
});

// --- Forgot password: a 6-digit code by email (Cloudflare Email Service, email.ts) ---

function newResetCode(): string {
  // 0–999999 without modulo bias: reject the top of the 32-bit range.
  const buf = new Uint32Array(1);
  do crypto.getRandomValues(buf);
  while (buf[0] >= 4_294_000_000);
  return String(buf[0] % 1_000_000).padStart(6, "0");
}

// Same answer whether or not the email has an account, so the form cannot be used to find
// accounts. Errors only for a bad address, too many requests, or a sending failure.
export const requestPasswordReset = action({
  args: { email: v.string() },
  handler: async (ctx, args): Promise<{ sent: true }> => {
    const email = normalizeEmail(args.email);
    if (email.length > MAX_EMAIL || !EMAIL.test(email)) {
      throw new ConvexError("Enter a valid email address.");
    }
    if (!emailConfigured()) throw new ConvexError(EMAIL_NOT_CONFIGURED);
    const code = newResetCode();
    const started = await ctx.runMutation(internal.users.startPasswordReset, { email, codeHash: tokenHash(code) });
    if (started === false) {
      throw new ConvexError("Too many reset emails. Try again in an hour.");
    }
    if (started === true) {
      await sendEmail(
        email,
        `Your Nexus reset code: ${code}`,
        `Your Nexus password reset code is ${code}.

It expires in 15 minutes. If you didn't ask for this, ignore this email; your password stays the same.`,
        `<p>Your Nexus password reset code is</p><p style="font-size:28px;font-weight:bold;letter-spacing:6px">${code}</p>` +
          `<p>It expires in 15 minutes. If you didn't ask for this, ignore this email; your password stays the same.</p>`,
      );
    }
    return { sent: true };
  },
});

// Sets a new password with the emailed code. Signs out every other session and returns a new one.
export const resetPassword = action({
  args: { email: v.string(), code: v.string(), newPassword: v.string() },
  handler: async (ctx, args): Promise<{ token: string }> => {
    const email = normalizeEmail(args.email);
    const code = args.code.replace(/\s/g, "");
    if (!/^\d{6}$/.test(code)) throw new ConvexError("Enter the 6-digit code from the email.");
    if (args.newPassword.length < MIN_PASSWORD) {
      throw new ConvexError(`Use at least ${MIN_PASSWORD} characters for the password.`);
    }
    if (args.newPassword.length > MAX_PASSWORD) {
      throw new ConvexError(`Use at most ${MAX_PASSWORD} characters for the password.`);
    }
    const token = newToken();
    const res = await ctx.runMutation(internal.users.finishPasswordReset, {
      email,
      codeHash: tokenHash(code),
      passwordHash: hashPassword(args.newPassword),
      newTokenHash: tokenHash(token),
    });
    if (!res.ok) throw new ConvexError(res.error);
    return { token };
  },
});

// FR-03: the session is deleted server-side.
export const signOut = mutation({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const session = await ctx.db
      .query("sessions")
      .withIndex("by_tokenHash", (q) => q.eq("tokenHash", tokenHash(args.token)))
      .unique();
    if (session) await ctx.db.delete(session._id);
  },
});

// Who is signed in with this token; null when signed out or expired.
export const me = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const found = await sessionUser(ctx, args.token);
    return found ? { email: found.user.email } : null;
  },
});

// --- Profile and Account & Security (app Settings) ---

export const profile = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const devices = await ctx.db
      .query("devices")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect();
    const avatarUrl = user.avatarFileId ? await ctx.storage.getUrl(user.avatarFileId) : null;
    return {
      email: user.email,
      name: user.name ?? null,
      createdAt: user.createdAt,
      deviceCount: devices.length,
      avatar: user.avatar ?? null,
      avatarUrl,
    };
  },
});

// Built-in pixel characters the app can draw (keep in sync with the app's PixelAvatars).
const PIXEL_AVATARS = ["explorer", "ranger", "coder", "gamer", "cat", "fox", "owl", "tree", "mountain"];
const MAX_AVATAR_BYTES = 512 * 1024;

// Step 1 of a photo upload: a one-time URL the app POSTs the (already resized) image to.
export const avatarUploadUrl = mutation({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    await requireUserAndTouch(ctx, args.token);
    return await ctx.storage.generateUploadUrl();
  },
});

// Step 2: pick a pixel character, attach the uploaded photo, or clear the picture.
export const setAvatar = mutation({
  args: {
    token: v.string(),
    pixel: v.optional(v.string()),
    fileId: v.optional(v.id("_storage")),
  },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    let avatar: string | undefined;
    let avatarFileId: typeof user.avatarFileId;
    if (args.fileId) {
      const meta = await ctx.db.system.get(args.fileId);
      if (!meta) throw new ConvexError("Upload not found. Try again.");
      if (!meta.contentType?.startsWith("image/") || meta.size > MAX_AVATAR_BYTES) {
        await ctx.storage.delete(args.fileId);
        throw new ConvexError("Use an image under 512 KB.");
      }
      avatar = "photo";
      avatarFileId = args.fileId;
    } else if (args.pixel) {
      if (!PIXEL_AVATARS.includes(args.pixel)) throw new ConvexError("Unknown avatar.");
      avatar = `pixel:${args.pixel}`;
    }
    // Replace: the previous photo is no longer referenced, so remove it.
    if (user.avatarFileId && user.avatarFileId !== avatarFileId) {
      await ctx.storage.delete(user.avatarFileId);
    }
    await ctx.db.patch(user._id, { avatar, avatarFileId });
    return { avatar: avatar ?? null, avatarUrl: avatarFileId ? await ctx.storage.getUrl(avatarFileId) : null };
  },
});

export const updateProfile = mutation({
  args: { token: v.string(), name: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUserAndTouch(ctx, args.token);
    const name = cleanName(args.name);
    if (!name) throw new ConvexError("Enter a name (up to 40 characters).");
    await ctx.db.patch(user._id, { name });
    return { name };
  },
});

// Needs the current password. Other sessions are signed out; this one stays signed in.
export const changePassword = action({
  args: { token: v.string(), currentPassword: v.string(), newPassword: v.string() },
  handler: async (ctx, args): Promise<{ ok: true }> => {
    if (args.newPassword.length < MIN_PASSWORD) {
      throw new ConvexError(`Use at least ${MIN_PASSWORD} characters for the new password.`);
    }
    if (args.newPassword.length > MAX_PASSWORD || args.currentPassword.length > MAX_PASSWORD) {
      throw new ConvexError(`Use at most ${MAX_PASSWORD} characters for the password.`);
    }
    const current = await ctx.runQuery(internal.users.passwordHashForSession, { tokenHash: tokenHash(args.token) });
    if (!current) throw new ConvexError("signed out");
    // Wrong current passwords count toward the same 5 per 15 min limit as sign-in.
    if ((await ctx.runQuery(internal.users.recentAttempts, { email: current.email })) >= MAX_ATTEMPTS) {
      throw new ConvexError("Too many attempts. Try again in 15 minutes.");
    }
    if (!verifyPassword(args.currentPassword, current.passwordHash)) {
      await ctx.runMutation(internal.users.recordAttempt, { email: current.email });
      throw new ConvexError("Your current password is wrong.");
    }
    await ctx.runMutation(internal.users.setPasswordKeepSession, {
      userId: current.userId,
      passwordHash: hashPassword(args.newPassword),
      keepTokenHash: tokenHash(args.token),
    });
    return { ok: true };
  },
});

// Signs out every session of this account, including the one making the call.
export const signOutEverywhere = mutation({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const user = await requireUser(ctx, args.token);
    const sessions = await ctx.db
      .query("sessions")
      .withIndex("by_user", (q) => q.eq("userId", user._id))
      .collect();
    for (const s of sessions) await ctx.db.delete(s._id);
    return { count: sessions.length };
  },
});

// GDPR Art. 17: Right to Erasure / Account Deletion.
// Cascades across all devices, sessions, latest readings, and summaries owned by this user.
export const deleteAccount = mutation({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const found = await sessionUser(ctx, args.token);
    if (!found) {
      throw new ConvexError("Not authenticated.");
    }
    const userId = found.user._id;
    if (found.user.avatarFileId) await ctx.storage.delete(found.user.avatarFileId);
    // Computers shared with this account: drop the access (the owners keep their computers).
    for (const sh of await ctx.db
      .query("deviceShares")
      .withIndex("by_user", (q) => q.eq("userId", userId))
      .collect()) {
      await ctx.db.delete(sh._id);
    }

    // 1. Delete all sessions for this user
    const userSessions = await ctx.db
      .query("sessions")
      .withIndex("by_user", (q) => q.eq("userId", userId))
      .collect();
    for (const s of userSessions) {
      await ctx.db.delete(s._id);
    }

    // 2. Delete all devices and their associated telemetry data
    const userDevices = await ctx.db
      .query("devices")
      .withIndex("by_user", (q) => q.eq("userId", userId))
      .collect();

    for (const d of userDevices) {
      // Delete latest records for this device
      const latestRows = await ctx.db
        .query("latest")
        .withIndex("by_device", (q) => q.eq("deviceId", d._id))
        .collect();
      for (const l of latestRows) {
        await ctx.db.delete(l._id);
      }

      // Delete summaries for this device
      const summaryRows = await ctx.db
        .query("summaries")
        .withIndex("by_device_time", (q) => q.eq("deviceId", d._id))
        .collect();
      for (const sm of summaryRows) {
        await ctx.db.delete(sm._id);
      }

      // Delete pairing requests for this device
      const pairingRows = await ctx.db
        .query("pairingRequests")
        .withIndex("by_secretHash", (q) => q.eq("secretHash", d.secretHash))
        .collect();
      for (const pr of pairingRows) {
        await ctx.db.delete(pr._id);
      }

      // Delete per-drive thresholds for this device
      for (const dt of await ctx.db
        .query("diskThresholds")
        .withIndex("by_device", (q) => q.eq("deviceId", d._id))
        .collect()) {
        await ctx.db.delete(dt._id);
      }

      // Delete the device row
      await ctx.db.delete(d._id);
    }

    // 3. Delete alerts, thresholds, push tokens and open reset codes
    const accountRows = [
      ...(await ctx.db
        .query("passwordResets")
        .withIndex("by_user", (q) => q.eq("userId", userId))
        .collect()),
      ...(await ctx.db
        .query("alerts")
        .withIndex("by_user_time", (q) => q.eq("userId", userId))
        .collect()),
      ...(await ctx.db
        .query("thresholds")
        .withIndex("by_user", (q) => q.eq("userId", userId))
        .collect()),
      ...(await ctx.db
        .query("pushTokens")
        .withIndex("by_user", (q) => q.eq("userId", userId))
        .collect()),
    ];
    for (const r of accountRows) {
      await ctx.db.delete(r._id);
    }

    // 4. Delete the user
    await ctx.db.delete(userId);
    return { success: true };
  },
});

// GDPR Art. 20: Right to Data Portability.
// Exports all user profile information, registered devices, and historical telemetry as machine-readable JSON.
export const exportData = query({
  args: { token: v.string() },
  handler: async (ctx, args) => {
    const found = await sessionUser(ctx, args.token);
    if (!found) {
      throw new ConvexError("Not authenticated.");
    }
    const userId = found.user._id;

    const userDevices = await ctx.db
      .query("devices")
      .withIndex("by_user", (q) => q.eq("userId", userId))
      .collect();

    const devicesData = [];
    for (const d of userDevices) {
      const latest = await ctx.db
        .query("latest")
        .withIndex("by_device", (q) => q.eq("deviceId", d._id))
        .first();

      const summaries = await ctx.db
        .query("summaries")
        .withIndex("by_device_time", (q) => q.eq("deviceId", d._id))
        .order("desc")
        .take(100);

      devicesData.push({
        name: d.name,
        capabilities: d.capabilities,
        lastSeenAt: d.lastSeenAt,
        latest,
        recentSummaries: summaries,
      });
    }

    return {
      user: {
        email: found.user.email,
        createdAt: found.user.createdAt,
      },
      exportDate: Date.now(),
      devices: devicesData,
    };
  },
});
