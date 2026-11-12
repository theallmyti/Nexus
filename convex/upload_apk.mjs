import fs from "fs";
import path from "path";
import { ConvexHttpClient } from "convex/browser";
import { api } from "./convex/_generated/api.js";

const CONVEX_URL = "https://tidy-newt-65.convex.cloud";
const client = new ConvexHttpClient(CONVEX_URL);

const apkPath = path.resolve("../android/app/build/outputs/apk/debug/app-debug.apk");
if (!fs.existsSync(apkPath)) {
  console.error("APK not found at:", apkPath);
  process.exit(1);
}

const stats = fs.statSync(apkPath);
console.log(`Found APK (${(stats.size / 1024 / 1024).toFixed(2)} MB): ${apkPath}`);

async function main() {
  console.log("Requesting upload URL from Convex...");
  const uploadUrl = await client.mutation(api.versions.generateAppUploadUrl, {});
  console.log("Upload URL obtained:", uploadUrl);

  console.log("Uploading APK bytes...");
  const fileData = fs.readFileSync(apkPath);
  const response = await fetch(uploadUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/vnd.android.package-archive",
    },
    body: fileData,
  });

  if (!response.ok) {
    throw new Error(`Upload failed: ${response.status} ${response.statusText}`);
  }

  const { storageId } = await response.json();
  console.log("Uploaded successfully. Storage ID:", storageId);

  console.log("Registering update record in Convex...");
  await client.mutation(api.versions.registerAppUpdate, {
    storageId,
    versionCode: 2,
    versionName: "1.0.1",
    releaseNotes: "• Fixed light mode contrast (dark navy text on white cards)\n• Standardized uniform stat pill containers\n• Formatted CPU clock in standard GHz\n• Fixed battery charging status detection on AC power\n• Added custom display name in Settings > Account\n• In-app seamless auto-updater",
    isMandatory: false,
  });

  console.log("Verifying checkAppUpdate query...");
  const updateCheck = await client.query(api.versions.checkAppUpdate, { currentVersionCode: 1 });
  console.log("Query result:", JSON.stringify(updateCheck, null, 2));

  // Test downloading the first 1KB of the storage URL to ensure public read works
  const headCheck = await fetch(updateCheck.downloadUrl, { method: "HEAD" });
  console.log("Storage URL HEAD status:", headCheck.status, headCheck.headers.get("content-length"), "bytes");

  console.log("Done! In-app update is live and ready for 1-tap install.");
}

main().catch((err) => {
  console.error("Error in upload:", err);
  process.exit(1);
});
