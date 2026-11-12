// Transactional email through Cloudflare Email Service (REST API; Convex is not a Worker, so the
// Workers `send_email` binding is not available). Environment variables (Convex dashboard →
// Settings → Environment Variables):
//   CF_EMAIL_ACCOUNT_ID  Cloudflare account id
//   CF_EMAIL_API_TOKEN   API token allowed to send email for that account
//   EMAIL_FROM           sender on a domain onboarded to Email Sending, e.g. "noreply@example.com"
// https://developers.cloudflare.com/email-service/api/send-emails/rest-api/
import { ConvexError } from "convex/values";

export const EMAIL_NOT_CONFIGURED = "Password reset email isn't set up on this server yet.";

export function emailConfigured(): boolean {
  return Boolean(process.env.CF_EMAIL_ACCOUNT_ID && process.env.CF_EMAIL_API_TOKEN && process.env.EMAIL_FROM);
}

export async function sendEmail(to: string, subject: string, text: string, html: string): Promise<void> {
  const accountId = process.env.CF_EMAIL_ACCOUNT_ID;
  const apiToken = process.env.CF_EMAIL_API_TOKEN;
  const from = process.env.EMAIL_FROM;
  if (!accountId || !apiToken || !from) throw new ConvexError(EMAIL_NOT_CONFIGURED);

  let res: Response;
  try {
    res = await fetch(`https://api.cloudflare.com/client/v4/accounts/${encodeURIComponent(accountId)}/email/sending/send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${apiToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ from, to, subject, text, html }),
    });
  } catch {
    throw new ConvexError("Couldn't send the email right now. Please try again in a minute.");
  }
  const body = (await res.json().catch(() => null)) as
    | { success?: boolean; errors?: { code: number; message: string }[]; result?: { permanent_bounces?: string[] } }
    | null;
  if (!res.ok || !body?.success) {
    // Log the provider's reason for the owner; never echo it (or the token) to the caller.
    console.error("email send failed", res.status, JSON.stringify(body?.errors ?? []));
    throw new ConvexError("Couldn't send the email right now. Please try again in a minute.");
  }
  if (body.result?.permanent_bounces?.length) {
    throw new ConvexError("That email address can't receive mail. Check it and try again.");
  }
}
