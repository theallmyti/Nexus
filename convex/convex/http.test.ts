import { expect, test } from "vitest";
import { handlePair, handleTurnstile, GITHUB_RELEASES_URL } from "./http";
import { TURNSTILE_SITEKEY } from "./turnstile";

test("GET /pair returns smart pairing HTML with deep link and APK download fallback", async () => {
  const request = new Request("https://tidy-newt-65.convex.site/pair?code=ABC234");
  const response = await handlePair(request);
  expect(response.status).toBe(200);
  expect(response.headers.get("Content-Type")).toContain("text/html");

  const html = await response.text();
  expect(html).toContain("ABC 234");
  expect(html).toContain("nexus://pair?code=ABC234");
  expect(html).toContain(GITHUB_RELEASES_URL);
});

test("GET /turnstile renders the sign-up widget with a per-response script nonce", async () => {
  const a = handleTurnstile();
  const b = handleTurnstile();
  const html = await a.text();
  expect(html).toContain(TURNSTILE_SITEKEY);
  expect(html).toContain('action: "signup"');
  const csp = a.headers.get("Content-Security-Policy") ?? "";
  const nonce = /'nonce-([0-9a-f]+)'/.exec(csp)?.[1];
  expect(nonce).toBeTruthy();
  expect(html).toContain(`nonce="${nonce}"`);
  expect(csp).not.toContain("'unsafe-inline' https://challenges"); // scripts only by nonce or from Cloudflare
  expect(b.headers.get("Content-Security-Policy")).not.toBe(csp);
});
