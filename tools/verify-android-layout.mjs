import { access } from "node:fs/promises";

const required = [
  "android/settings.gradle.kts",
  "android/build.gradle.kts",
  "android/app/build.gradle.kts",
  "android/app/src/main/AndroidManifest.xml",
  "android/app/src/main/java/dev/wellfriend/scan/MainActivity.kt",
];
await Promise.all(required.map((path) => access(path)));
console.log(`Android skeleton contract passed for ${required.length} required files`);

