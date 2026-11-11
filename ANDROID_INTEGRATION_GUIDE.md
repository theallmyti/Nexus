# Android Kotlin Integration Guide — Nexus

> **Note for the Android Developer / AI Coding Agent:**  
> This document specifies all requirements, contracts, API calls, and git synchronization instructions for building and connecting the Android app (Kotlin + Jetpack Compose) to the **Nexus** fleet monitoring system.

---

## 1. Git Repository Synchronization

The project repository and software name have been officially renamed to **Nexus**.

If you already have a local git clone, run the following commands in your project terminal to update your remote tracking:

```bash
# 1. Update the remote URL
git remote set-url origin https://github.com/theallmyti/Nexus.git

# 2. Fetch and pull the latest changes
git pull origin main
```

If cloning fresh:
```bash
git clone https://github.com/theallmyti/Nexus.git
```

---

## 2. App Branding & Identity

- **Application Name**: `Nexus`
- Update in `android/app/src/main/res/values/strings.xml`:
  ```xml
  <resources>
      <string name="app_name">Nexus</string>
  </resources>
  ```

---

## 3. Device Pairing Specification (Critical Contract)

When a user installs the Nexus agent on a PC/server, the computer prints an interactive QR code and a 6-character code in the terminal or setup window.

### QR Code Format
The QR code scanned from the computer now encodes:
```text
NEXUS-PAIR:ABC123
```
*(Older legacy agents may produce `PCMONITOR-PAIR:ABC123`).*

### Kotlin Scanner & Normalization Implementation
When scanning with Google Code Scanner / CameraX or when accepting manual text entry, sanitize the code using this normalization helper:

```kotlin
object PairingHelper {
    private val CODE_REGEX = Regex("^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{6}$")

    /**
     * Strips QR prefixes, whitespace, and hyphens to extract the 6-character code.
     */
    fun normalizeCode(rawInput: String): String {
        return rawInput
            .replace("NEXUS-PAIR:", "", ignoreCase = true)
            .replace("PCMONITOR-PAIR:", "", ignoreCase = true)
            .replace(Regex("[\\s-]"), "")
            .uppercase()
    }

    /**
     * Validates if the normalized code meets the 6-character code pattern.
     */
    fun isValidCode(normalizedCode: String): Boolean {
        return CODE_REGEX.matches(normalizedCode)
    }
}
```

### Pairing Flow Steps
1. User points camera at QR code (or enters the 6-character code in a text field).
2. Call `dashboard.checkPairingCode(token, code)`:
   - Validates the code before claiming it and returns computer metadata: `{ name: String, os: String, state: "pending" | "confirmed" | "expired" }`.
   - Displays confirmation dialog: *"Pair **<name>** (<os>) with your Nexus?"*.
3. User confirms -> Call `dashboard.confirmPairing(token, code)`:
   - Links the device to the signed-in user's account.

---

## 4. Backend Convex Client & API Contracts

The Android app communicates with the **Convex cloud backend** via WebSocket using the Convex Android / Java SDK.

### Session Authentication & Storage
- Authentication functions return a random 256-bit `token` string.
- Store this token securely in Android's **`EncryptedSharedPreferences`**.
- Pass this `token` argument in every query and mutation.
- If a query fails with session expired or invalid, navigate back to the Sign-In screen.

### Endpoints Reference

#### Authentication
| Function | Type | Arguments | Description |
|---|---|---|---|
| `auth.signUp` | Action | `email: string, password: string` | Creates account (min 8 char password). Returns `{ token: string }`. |
| `auth.signIn` | Action | `email: string, password: string` | Signs in (rate-limited to 5 tries / 15 min). Returns `{ token: string }`. |
| `auth.signOut` | Mutation | `token: string` | Revokes the current session token. |
| `auth.me` | Query | `token: string` | Returns `{ email: string }` if valid, or `null` if session expired. |

#### Fleet & Device Monitoring
| Function | Type | Arguments | Description |
|---|---|---|---|
| `dashboard.overview` | Query (Subscribe) | `token: string` | Returns all devices owned by user with their online status and latest stats. |
| `dashboard.setViewer` | Mutation | `token: string, deviceId: Id<"devices">` | **Viewer lease**: Keeps per-second live data flowing for this device for 30 seconds. |
| `dashboard.setViewerAll` | Mutation | `token: string` | **Viewer lease**: Renews the 30-second live data flow for all user devices. |
| `dashboard.history` | Query | `token: string, deviceId: Id<"devices">, sinceMs: number, untilMs?: number` | Fetches 1-minute historical summaries for charts (1h / 6h / 24h). |
| `dashboard.renameDevice` | Mutation | `token: string, deviceId: Id<"devices">, name: string` | Renames a device (1–64 characters). |
| `dashboard.removeDevice` | Mutation | `token: string, deviceId: Id<"devices">` | Unlinks and purges a device from the account. |

---

## 5. Live Data Heartbeat Rule (Crucial for Quota)

To conserve cloud bandwidth and stay within free quotas, **Nexus agents only stream per-second live stats when someone is watching.**

- **When the Device Detail screen or Fleet screen is visible**:
  - The Android app **MUST** call `dashboard.setViewer(token, deviceId)` (or `dashboard.setViewerAll(token)`) every **15 seconds**.
- **When the screen is closed or app goes to the background**:
  - Stop sending the heartbeat.
  - The agent's 30-second lease will expire automatically and the agent will go quiet.

Example ViewModel / Coroutine pattern:
```kotlin
// In your ViewModel / Screen lifecycle
fun startLiveViewing(deviceId: String) {
    viewModelScope.launch {
        while (isActive) {
            convexClient.mutation("dashboard:setViewer", mapOf("token" to token, "deviceId" to deviceId))
            delay(15_000) // repeat every 15 seconds
        }
    }
}
```

---

## 6. Device Data Model (What the Agent Sends)

When subscribing to `dashboard.overview` or inspecting `device.latest`:

```typescript
{
  id: string,
  name: string,
  online: boolean,            // true if lastSeenAt is within 3 minutes
  lastSeenAt: number,
  capabilities: {
    cpuModel?: string,
    ramSpeedMTs?: number,
    hasBattery: boolean,
    hasNvidiaGpu: boolean,
    perCoreTemps: boolean,
    windowsMode?: "full" | "noDriver",
    isPi: boolean,
    drives: [{ name: string, totalGB: number }]
  },
  latest?: {
    ts: number,
    cpu: {
      util: number,           // 0-100%
      packageTemp?: number,   // °C
      perCoreUtil?: number[], // % per core
      clocks?: number[]       // MHz per core
    },
    ram: {
      usedPct: number,        // 0-100%
      detail?: { used: number, free: number, total: number, swapUsed: number, swapTotal: number },
      topProcs?: [{ name: string, mb: number }]
    },
    disks?: [{ name: string, totalGB: number, freeGB: number }],
    gpu?: {
      temp: number,           // °C
      util: number,           // 0-100%
      vramUsed: number,       // MB
      vramTotal: number,      // MB
      powerW: number,         // Watts
      clock: number,          // MHz
      fanPct?: number         // %
    },
    battery?: {
      pct: number,            // 0-100%
      charging: boolean,
      healthPct: number,      // %
      rateW: number,          // Watts
      cycles?: number
    },
    uptimeSec?: number
  }
}
```

### Threshold Defaults (for Gauges & Warning Colors)
- **CPU Temp warning**: $\ge 90^\circ\text{C}$
- **GPU Temp warning**: $\ge 85^\circ\text{C}$
- **RAM Util warning**: $\ge 90\%$
- **Disk Free warning**: $\le 10\%$ remaining

---

## 7. Checklist for the Android Agent / Developer

- [ ] Sync git remote to `https://github.com/theallmyti/Nexus.git`.
- [ ] Set `app_name` to `"Nexus"` in `strings.xml`.
- [ ] Implement `PairingHelper.normalizeCode` supporting both `NEXUS-PAIR:` and `PCMONITOR-PAIR:`.
- [ ] Call `dashboard.checkPairingCode` and `dashboard.confirmPairing` on QR scan / manual code entry.
- [ ] Store session `token` in `EncryptedSharedPreferences`.
- [ ] Implement the 15-second `setViewer` / `setViewerAll` heartbeat while screens are visible.
