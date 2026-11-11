import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";

const http = httpRouter();

export const GITHUB_RELEASES_URL = "https://github.com/theallmyti/Nexus/releases/latest";

export async function handlePair(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const code = (url.searchParams.get("code") ?? "").toUpperCase().replace(/[^23456789ABCDEFGHJKLMNPQRSTUVWXYZ]/g, "");
  const formattedCode = code.length === 6 ? `${code.slice(0, 3)} ${code.slice(3)}` : code;
  const deepLink = `nexus://pair?code=${encodeURIComponent(code)}`;

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Nexus — Pair Device</title>
  <style>
    :root {
      --bg: #090d16;
      --card-bg: #111827;
      --border: #1f2937;
      --text: #f9fafb;
      --text-muted: #9ca3af;
      --accent: #3b82f6;
      --accent-hover: #2563eb;
      --code-bg: #1e293b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 1rem;
      max-width: 420px;
      width: 100%;
      padding: 2rem;
      text-align: center;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
    }
    .logo {
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: 0.1em;
      color: var(--accent);
      margin-bottom: 0.5rem;
      text-transform: uppercase;
    }
    h1 {
      font-size: 1.5rem;
      font-weight: 600;
      margin-bottom: 0.5rem;
    }
    p.desc {
      font-size: 0.875rem;
      color: var(--text-muted);
      margin-bottom: 1.5rem;
      line-height: 1.4;
    }
    .code-box {
      background: var(--code-bg);
      border: 1px dashed var(--accent);
      border-radius: 0.75rem;
      padding: 1rem;
      margin-bottom: 1.5rem;
    }
    .code-label {
      font-size: 0.75rem;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 0.25rem;
    }
    .code-value {
      font-family: monospace;
      font-size: 2rem;
      font-weight: 700;
      letter-spacing: 0.15em;
      color: #60a5fa;
    }
    .btn {
      display: inline-block;
      width: 100%;
      padding: 0.875rem 1rem;
      border-radius: 0.5rem;
      font-size: 0.95rem;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.2s;
      cursor: pointer;
      border: none;
      margin-bottom: 0.75rem;
    }
    .btn-primary {
      background: var(--accent);
      color: #fff;
    }
    .btn-primary:hover {
      background: var(--accent-hover);
    }
    .btn-secondary {
      background: #1f2937;
      color: var(--text);
      border: 1px solid #374151;
    }
    .btn-secondary:hover {
      background: #374151;
    }
    .steps {
      text-align: left;
      margin-top: 1.5rem;
      padding-top: 1.25rem;
      border-top: 1px solid var(--border);
    }
    .steps h3 {
      font-size: 0.875rem;
      color: var(--text-muted);
      margin-bottom: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .steps ol {
      padding-left: 1.25rem;
      font-size: 0.85rem;
      color: var(--text-muted);
      line-height: 1.6;
    }
    .steps li { margin-bottom: 0.4rem; }
    .steps strong { color: var(--text); }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">Nexus Fleet Monitor</div>
    <h1>Pairing Computer</h1>
    <p class="desc">Scan detected. Opening the Nexus mobile app to confirm this device...</p>

    <div class="code-box">
      <div class="code-label">Pairing Code</div>
      <div class="code-value">${formattedCode || "N/A"}</div>
    </div>

    <a href="${deepLink}" class="btn btn-primary" id="openAppBtn">Open in Nexus App</a>
    <a href="${GITHUB_RELEASES_URL}" class="btn btn-secondary" target="_blank" rel="noopener">Download Nexus Android APK</a>

    <div class="steps">
      <h3>Don't have the app yet?</h3>
      <ol>
        <li>Tap <strong>Download Nexus Android APK</strong> to get the latest release from GitHub.</li>
        <li>Install the APK and open <strong>Nexus</strong>.</li>
        <li>Sign in, tap <strong>Pair Device</strong>, and enter code <strong>${formattedCode || "above"}</strong>.</li>
      </ol>
    </div>
  </div>

  <script>
    // Automatically attempt deep link if a code is present
    const code = "${code}";
    if (code) {
      window.location.href = "${deepLink}";
    }
  </script>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
    },
  });
}

http.route({
  path: "/pair",
  method: "GET",
  handler: httpAction(async (_ctx, request) => handlePair(request)),
});

export default http;
