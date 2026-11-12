// Production schema for Nexus Fleet Monitoring System (Convex)
// Changes must be backward-compatible (add optional fields only) — docs/Development.md §3.
import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";
import { battery, capabilities, collectorStatus, driveLive, driveSummary, ramDetail, topProc, usage } from "./validators";

export default defineSchema({
  // Accounts (docs/Database.md users, sessions, loginAttempts; docs/Security.md §3–4).
  users: defineTable({
    email: v.string(), // lowercase, unique
    passwordHash: v.string(), // scrypt$N$r$p$salt$key
    createdAt: v.number(),
    setupDone: v.boolean(), // first-run threshold setup finished (FR-40)
  }).index("by_email", ["email"]),

  sessions: defineTable({
    userId: v.id("users"),
    tokenHash: v.string(), // SHA-256 of the random session token
    lastUsedAt: v.number(), // expires after 30 days without use (FR-02)
  })
    .index("by_tokenHash", ["tokenHash"])
    .index("by_user", ["userId"])
    .index("by_lastUsedAt", ["lastUsedAt"]),

  // Failed sign-ins, for the 5 per 15 min limit (FR-04).
  loginAttempts: defineTable({
    email: v.string(),
    at: v.number(),
  })
    .index("by_email_at", ["email", "at"])
    .index("by_at", ["at"]),

  // Other rate limits (rateLimit.ts): sign-ups, pairing codes, wrong codes. Deleted after a day.
  rateEvents: defineTable({
    key: v.string(), // e.g. "signup", "pair:<secretHash>", "wrongCode:<userId>"
    at: v.number(),
  })
    .index("by_key_at", ["key", "at"])
    .index("by_at", ["at"]),

  devices: defineTable({
    // Owner, set when a signed-in user confirms pairing. Every read checks it (docs/Security.md §2).
    userId: v.optional(v.id("users")),
    name: v.string(),
    secretHash: v.string(), // SHA-256 hex of the device secret (docs/Security.md §6)
    lastSeenAt: v.number(),
    isFake: v.boolean(),
    online: v.optional(v.boolean()),
    hostname: v.optional(v.string()),
    kind: v.optional(v.string()),
    os: v.optional(v.string()),
    pairedAt: v.optional(v.number()),
    // Live data flows only while this is in the future (docs/Flow.md §4, D3).
    viewerUntil: v.optional(v.number()),
    // Server time of the last accepted summary, for the 50 s limit (docs/Security.md §2).
    lastSummaryAt: v.optional(v.number()),
    // Reported by the agent at registration (FR-15).
    capabilities: v.optional(capabilities),
    agentVersion: v.optional(v.string()),
  })
    .index("by_secretHash", ["secretHash"])
    .index("by_user", ["userId"])
    .index("by_user_hostname", ["userId", "hostname"]),

  // Open pairing codes (docs/Flow.md §3). Confirmed rows keep the device link for the agent.
  pairingRequests: defineTable({
    code: v.string(),
    secretHash: v.string(),
    name: v.string(),
    os: v.string(),
    status: v.union(v.literal("pending"), v.literal("confirmed")),
    deviceId: v.optional(v.id("devices")),
    userId: v.optional(v.union(v.id("users"), v.string())),
    expiresAt: v.number(),
  })
    .index("by_code", ["code"])
    .index("by_secretHash", ["secretHash"])
    .index("by_expires", ["expiresAt"]),

  latest: defineTable({
    deviceId: v.id("devices"),
    userId: v.optional(v.union(v.id("users"), v.string())),
    ts: v.number(),
    cpu: v.object({
      util: v.number(),
      packageTemp: v.optional(v.number()),
      perCoreUtil: v.optional(v.array(v.number())), // % per logical core
      clocks: v.optional(v.array(v.number())), // MHz per logical core
    }),
    ram: v.object({
      usedPct: v.number(),
      detail: v.optional(ramDetail),
      topProcs: v.optional(v.array(topProc)),
    }),
    disks: v.optional(v.array(driveLive)),
    uptimeSec: v.optional(v.number()),
    usage: v.optional(usage),
    battery: v.optional(battery),
    // Optional: only NVIDIA machines send it. Units: MB, W, MHz, %.
    gpu: v.optional(
      v.object({
        temp: v.number(),
        util: v.number(),
        vramUsed: v.number(),
        vramTotal: v.number(),
        powerW: v.number(),
        clock: v.number(),
        fanPct: v.optional(v.number()),
      }),
    ),
    // Optional: Raspberry Pi containers. Counts come from the home-server dashboard.
    pi: v.optional(
      v.object({
        docker: v.optional(v.object({ running: v.number(), stopped: v.number(), errored: v.number() })),
      }),
    ),
  }).index("by_device", ["deviceId"]),

  // One row per device per minute: averages and peaks of the agent's 1 s samples
  // (docs/Database.md summaries). Collector status is added in Task 14.
  summaries: defineTable({
    deviceId: v.id("devices"),
    minuteTs: v.number(),
    cpuUtilAvg: v.number(),
    cpuUtilMax: v.number(),
    ramPctAvg: v.number(),
    ramPctMax: v.number(),
    cpuTempAvg: v.optional(v.number()),
    cpuTempMax: v.optional(v.number()),
    gpuTempMax: v.optional(v.number()),
    gpuUtilAvg: v.optional(v.number()),
    disks: v.optional(v.array(driveSummary)),
    batteryPct: v.optional(v.number()),
    collectorStatus: v.optional(collectorStatus),
    userId: v.optional(v.union(v.id("users"), v.string())),
  })
    .index("by_device_time", ["deviceId", "minuteTs"])
    // For the daily retention job across all devices (FR-50).
    .index("by_minute", ["minuteTs"]),

  thresholds: defineTable({
    userId: v.id("users"),
    deviceId: v.optional(v.id("devices")),
    cpuTempC: v.optional(v.number()),
    gpuTempC: v.optional(v.number()),
    ramPct: v.optional(v.number()),
    // Alert when a drive has less than this % free (default for drives without their own row).
    diskFreePct: v.optional(v.number()),
  })
    .index("by_user", ["userId"])
    .index("by_device", ["deviceId"])
    .index("by_user_device", ["userId", "deviceId"]),

  diskThresholds: defineTable({
    deviceId: v.id("devices"),
    drive: v.string(),
    minFreePct: v.optional(v.number()),
    minFreeGB: v.optional(v.number()),
  })
    .index("by_device", ["deviceId"])
    .index("by_device_drive", ["deviceId", "drive"]),

  alerts: defineTable({
    userId: v.id("users"),
    deviceId: v.id("devices"),
    type: v.union(
      v.literal("cpuTemp"),
      v.literal("gpuTemp"),
      v.literal("ram"),
      v.literal("disk"),
      v.literal("offline"),
      v.literal("canary")
    ),
    value: v.optional(v.number()),
    threshold: v.optional(v.number()),
    // Disk alerts: which drive (e.g. "C:\\" or "/").
    drive: v.optional(v.string()),
    createdAt: v.number(),
    pushed: v.boolean(),
  })
    .index("by_user_time", ["userId", "createdAt"])
    .index("by_device_type_time", ["deviceId", "type", "createdAt"])
    // For the daily 7-day alert cleanup (cleanup.ts).
    .index("by_created", ["createdAt"]),

  pushTokens: defineTable({
    userId: v.id("users"),
    fcmToken: v.string(),
    phoneModel: v.string(),
    updatedAt: v.number(),
  })
    .index("by_user", ["userId"])
    .index("by_token", ["fcmToken"]),
});
