# Legal & Privacy Overview — Nexus

**Maintainers:** Aditya Prasad, Tirth Patel, Pratham Vasava (Vadodara, Gujarat, India)
**Code license:** [MIT License](LICENSE)
**Repository:** https://github.com/theallmyti/Nexus
**Privacy and grievance contact:** contact.nexus07@gmail.com
**Last updated:** 11 November 2026

This page is a summary. The full documents are binding:

| Document | What it covers |
|---|---|
| [docs/PRIVACY.md](docs/PRIVACY.md) | What data the hosted service collects, why, who processes it, how long it is kept, your rights |
| [docs/TERMS.md](docs/TERMS.md) | Rules for using the hosted service, monitoring-consent rules, driver risk, liability, governing law |
| [docs/COOKIE_POLICY.md](docs/COOKIE_POLICY.md) | Browser, phone and computer storage |
| [LICENSE](LICENSE) | MIT License for the source code |
| [docs/LEGAL_AUDIT_REPORT.md](docs/LEGAL_AUDIT_REPORT.md) | Internal review: what is done and what is still open |

---

## 1. Privacy in short
- **Collected:** email, salted scrypt password hash, device details (name, hostname, OS, CPU model, drives), hardware metrics (CPU, RAM, GPU, disks, battery, temperatures), and — on by default, can be turned off with `share_activity = false` — the top 5 program names by memory and boot/sleep/wake times.
- **Android app:** Firebase Cloud Messaging for alerts and **Google Analytics for Firebase** for app-usage statistics (includes the Android Advertising ID).
- **Not collected:** keystrokes, screenshots, file contents, browsing history, microphone, GPS location, MAC addresses.
- **Providers:** Convex (database, US/AWS), Cloudflare (dashboard hosting), Google Firebase (app notifications and analytics).
- **Retention:** minute summaries 7 days; account data until you delete it; backups rotate (8 most recent).
- **Rights:** self-service JSON export and account deletion in the dashboard; other requests by email. Applies under India's DPDP Act, 2023 and, for EU/UK users, the GDPR.
- **Age:** 18+ only.

## 2. Terms in short
- Free, non-commercial student project. No payments, no SLA; it may change or shut down.
- Install the agent **only** on computers you own or are allowed to monitor, and tell the people who use them. If you monitor someone else's computer, you are responsible for that person's data.
- The optional PawnIO kernel driver is third-party and used at your own risk.
- Readings and alerts are informational only — not for safety-critical use.
- Provided "as is"; liability capped at ₹1,000 where the law allows.
- Indian law; courts at Vadodara, Gujarat.

## 3. Storage in short
The web dashboard sets no cookies and has no trackers; it keeps one strictly necessary `localStorage` item (`pcm-session`). The Android app includes Google Analytics for Firebase.

## 4. Licenses and attributions
- **Nexus source code:** MIT License © 2026 Aditya Prasad, Tirth Patel, Pratham Vasava.
- **PawnIO modules** (optional Windows driver, by namazso): LGPL-2.1. The installer ships the license as `COPYING-PawnIO-Modules.txt`.
- **Third-party libraries:** the agent, dashboard and Android app use open-source libraries under their own licenses (mostly MIT, Apache-2.0, BSD, ISC, Zlib and Unicode-3.0, plus MPL-2.0 for `imbl` via the Convex Rust client). Their license texts are listed in each package manifest (`agent/Cargo.lock`, `dashboard/package.json`, `android/app/build.gradle.kts`); a generated `THIRD_PARTY_NOTICES` file is planned for release builds.

## 5. Who maintains what
| Area | Owner |
|---|---|
| Agent, backend (Convex), web dashboard, legal documents | Aditya Prasad |
| Android app, notifications (FCM), Firebase project | Tirth Patel |
| Android app UI | Pratham Vasava |

Any change that adds a new type of collected data, a new SDK, or a new provider **must update `docs/PRIVACY.md` in the same pull request.**
