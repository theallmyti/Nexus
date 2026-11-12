# Error Logging & Diagnostic Reference

Guidelines and procedures for diagnosing, logging, and triaging system errors across the Rust Agent, Convex Backend, and Android Client.

---

## 1. Rust Agent Logging (`agent/src/logging.rs`)
- **Format**: Rotating log files in `%LOCALAPPDATA%\Nexus\logs\` (Windows) or `~/.local/share/nexus/logs/` (Linux).
- **Levels**: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.
- **Diagnostic output**: Agent console and tray icon expose connection health and error reports.

## 2. Convex Cloud Backend Logging
- **Dashboard**: Real-time function execution logs via `https://dashboard.convex.dev`.
- **Error Types**:
  - `ConvexError("signed out")`: Unauthenticated caller or expired session token.
  - `ConvexError("Unauthorized: ...")`: Tenant isolation violation or cross-account IDOR attempt.
  - `ConvexError("Pairing code ...")`: Code expired (>10 min) or already confirmed.

## 3. Android Mobile Client Logging
- **Logcat Tags**:
  - `DeviceRepository`: Device synchronization, keepalive leases, and live snapshot queries.
  - `AlertRepository`: FCM push receipts and threshold evaluations.
  - `AuthRepository`: Session token persistence and authentication transitions.
- **Fail-safe Fallback**: Coroutine exception handlers ensure transient network disconnects do not crash the UI.
