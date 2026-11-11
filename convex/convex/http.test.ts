import { expect, test } from "vitest";
import { handlePair, GITHUB_RELEASES_URL } from "./http";

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
