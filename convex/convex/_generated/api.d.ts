/* eslint-disable */
/**
 * Generated `api` utility.
 *
 * THIS CODE IS AUTOMATICALLY GENERATED.
 *
 * To regenerate, run `npx convex dev`.
 * @module
 */

import type * as agent from "../agent.js";
import type * as alerts from "../alerts.js";
import type * as auth from "../auth.js";
import type * as cleanup from "../cleanup.js";
import type * as crons from "../crons.js";
import type * as dashboard from "../dashboard.js";
import type * as devices from "../devices.js";
import type * as http from "../http.js";
import type * as migrations from "../migrations.js";
import type * as model from "../model.js";
import type * as pairing from "../pairing.js";
import type * as passwords from "../passwords.js";
import type * as rateLimit from "../rateLimit.js";
import type * as summaries from "../summaries.js";
import type * as turnstile from "../turnstile.js";
import type * as users from "../users.js";
import type * as validators from "../validators.js";
import type * as versions from "../versions.js";

import type {
  ApiFromModules,
  FilterApi,
  FunctionReference,
} from "convex/server";

declare const fullApi: ApiFromModules<{
  agent: typeof agent;
  alerts: typeof alerts;
  auth: typeof auth;
  cleanup: typeof cleanup;
  crons: typeof crons;
  dashboard: typeof dashboard;
  devices: typeof devices;
  http: typeof http;
  migrations: typeof migrations;
  model: typeof model;
  pairing: typeof pairing;
  passwords: typeof passwords;
  rateLimit: typeof rateLimit;
  summaries: typeof summaries;
  turnstile: typeof turnstile;
  users: typeof users;
  validators: typeof validators;
  versions: typeof versions;
}>;

/**
 * A utility for referencing Convex functions in your app's public API.
 *
 * Usage:
 * ```js
 * const myFunctionReference = api.myModule.myFunction;
 * ```
 */
export declare const api: FilterApi<
  typeof fullApi,
  FunctionReference<any, "public">
>;

/**
 * A utility for referencing Convex functions in your app's internal API.
 *
 * Usage:
 * ```js
 * const myFunctionReference = internal.myModule.myFunction;
 * ```
 */
export declare const internal: FilterApi<
  typeof fullApi,
  FunctionReference<any, "internal">
>;

export declare const components: {};
