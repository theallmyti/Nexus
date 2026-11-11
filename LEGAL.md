# Legal & Privacy Overview — Nexus

**Publishers / Authors:** Aditya Prasad, Tirth Patel, Pratham Vasava  
**License:** [MIT License](LICENSE)  
**Repository:** https://github.com/theallmyti/Monitoring-System  
**Last Updated:** November 11, 2026  

---

## 1. Privacy Policy Summary
Nexus is an open-source, zero-surveillance system monitoring platform.
- **Data Collected:** Email address, salted scrypt/Argon2id password hash, device hostname, OS edition, CPU model/utilization/temperatures, RAM usage/speed (MT/s), GPU metrics (NVIDIA), and disk partition capacities.
- **Zero Surveillance:** Nexus strictly never collects keystrokes, personal files, screen captures, camera, microphone, or browsing history.
- **Subprocessors:** Convex Inc. (Cloud Database & Real-Time Sync, SOC 2 Type II, AES-256 encrypted at rest), Cloudflare Inc. (CDN, DNS, Edge network).
- **Retention:** Minute-level historical telemetry rollups are automatically retained for 30 days and purged by automated cleanup.
- **GDPR Rights:** Full self-service account deletion (Right to Erasure, GDPR Art. 17) and JSON data export (Right to Data Portability, GDPR Art. 20) are integrated directly into the dashboard.

For the full Privacy Policy, see [docs/PRIVACY.md](docs/PRIVACY.md).

---

## 2. Terms of Service Summary
- **Cost:** Nexus is free open-source software with no paid tiers, no subscription renewals, and no hidden fees.
- **Acceptable Use:** You may only install Nexus on machines you own or have explicit permission to monitor. Unauthorized surveillance and denial-of-service abuse are strictly prohibited.
- **Warranty Disclaimer:** The software and service are provided "AS IS", without warranty of any kind.
- **Liability Limitation:** Total liability is capped at $0.00.
- **Governing Law:** Standard international open-source principles.

For full Terms of Service, see [docs/TERMS.md](docs/TERMS.md).

---

## 3. Cookie & Web Storage Policy
Nexus uses **zero** advertising cookies, third-party analytics pixels, or marketing trackers.
The web dashboard uses only **strictly essential** web storage (`localStorage` key `pcm-session` for your cryptographic session token), which is exempt from consent banners under ePrivacy Directive & GDPR Art. 5(3).

For full details, see [docs/COOKIE_POLICY.md](docs/COOKIE_POLICY.md).

---

## 4. Licenses & Attributions
- **Core Platform:** MIT License © 2026 Aditya Prasad, Tirth Patel, Pratham Vasava.
- **Driver Modules:** PawnIO CPU temperature modules licensed under LGPL-2.1 by namazso.
- **Icons:** Lucide icons licensed under ISC/MIT.
