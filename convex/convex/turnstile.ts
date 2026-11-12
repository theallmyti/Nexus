// Cloudflare Turnstile bot check for sign-up (replaces the global sign-up cap, which anyone could
// trip to block every new account; docs/Security.md §2).
// Widget "nexus-signup" (managed). Its pages: /turnstile on this deployment (Android app, WebView)
// and the web dashboard. Environment variables (Convex dashboard → Settings → Environment Variables):
//   TURNSTILE_SECRET     the widget's secret key
//   TURNSTILE_HOSTNAMES  comma-separated frontend hostnames, e.g.
//                        "tidy-newt-65.convex.site,pc-monitor.pages.dev" (never localhost in prod)
import { ConvexError } from "convex/values";

export const TURNSTILE_SITEKEY = "0x4AAAAAAF-jgrx3EM0bgVT-";
const SITEVERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
const BOT_CHECK_FAILED = "Bot check failed. Please try again.";

// Fails closed: a missing configuration, a network error or any unexpected answer rejects.
export async function verifyTurnstile(token: string, expectedAction: string): Promise<void> {
  const secret = process.env.TURNSTILE_SECRET;
  const expectedHostnames = new Set(
    (process.env.TURNSTILE_HOSTNAMES ?? "")
      .split(",")
      .map((h) => h.trim())
      .filter(Boolean),
  );
  if (!secret || expectedHostnames.size === 0 || typeof token !== "string" || token.length === 0 || token.length > 2048) {
    throw new ConvexError(BOT_CHECK_FAILED);
  }
  let result: { success?: boolean; action?: string; hostname?: string };
  try {
    const r = await fetch(SITEVERIFY_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ secret, response: token }).toString(),
    });
    if (!r.ok) throw new Error(`siteverify ${r.status}`);
    result = await r.json();
  } catch {
    throw new ConvexError(BOT_CHECK_FAILED);
  }
  if (result.success !== true || result.action !== expectedAction || !expectedHostnames.has(result.hostname ?? "")) {
    throw new ConvexError(BOT_CHECK_FAILED);
  }
}
