import { access, readFile } from "node:fs/promises";

const required = [
  "android/settings.gradle.kts",
  "android/build.gradle.kts",
  "android/app/build.gradle.kts",
  "android/app/src/main/AndroidManifest.xml",
  "android/app/src/main/java/dev/wellfriend/scan/MainActivity.kt",
  "android/app/src/main/java/dev/wellfriend/scan/GalleryImportDecoder.kt",
  "android/scanner-core/build.gradle.kts",
  "android/scanner-perception/build.gradle.kts",
  "android/scanner-ui/build.gradle.kts",
  "android/scanner-export/build.gradle.kts",
  "android/scanner-testing/build.gradle.kts",
  "android/scanner-core/src/main/kotlin/dev/wellfriend/scan/core/ScanModels.kt",
  "android/scanner-core/src/main/kotlin/dev/wellfriend/scan/core/CoordinateMapping.kt",
  "android/scanner-perception/src/main/kotlin/dev/wellfriend/scan/perception/PerceptionContracts.kt",
  "android/scanner-perception/src/main/kotlin/dev/wellfriend/scan/perception/ScanController.kt",
  "android/scanner-ui/src/main/kotlin/dev/wellfriend/scan/ui/camera/CameraXScannerController.kt",
  "android/scanner-ui/src/main/kotlin/dev/wellfriend/scan/ui/overlay/DocumentOverlay.kt",
  "android/scanner-export/src/main/kotlin/dev/wellfriend/scan/export/ExportContracts.kt",
  "android/app/src/androidTest/README.md",
  "android/scanner-export/src/test/kotlin/dev/wellfriend/scan/export/ExportContractsTest.kt",
];
await Promise.all(required.map((path) => access(path)));
const contractSources = await Promise.all([
  "android/scanner-perception/src/main/kotlin/dev/wellfriend/scan/perception/PerceptionContracts.kt",
  "android/scanner-perception/src/main/kotlin/dev/wellfriend/scan/perception/ScanController.kt",
  "android/scanner-ui/src/main/kotlin/dev/wellfriend/scan/ui/camera/CameraXScannerController.kt",
  "android/scanner-ui/src/main/kotlin/dev/wellfriend/scan/ui/ScannerApp.kt",
  "android/scanner-ui/src/main/kotlin/dev/wellfriend/scan/ui/overlay/DocumentOverlay.kt",
  "android/scanner-export/src/main/kotlin/dev/wellfriend/scan/export/ExportContracts.kt",
].map((path) => readFile(path)));
const contract = Buffer.concat(contractSources).toString("utf8");
for (const marker of [
  "interface PerceptionEngine",
  "NativePerceptionBridge",
  "STRATEGY_KEEP_ONLY_LATEST",
  "CaptureReadiness",
  "PreviewCoordinateMapper",
  "ManualCropEditor",
  "ScanExporter",
]) {
  if (!contract.includes(marker)) throw new Error(`Android scanner contract is missing ${marker}`);
}
if (/mlkit|google\.ml\.kit/i.test(contract)) throw new Error("ML Kit must not become a required scanner dependency");
console.log(`Android scanner contract passed for ${required.length} required files and binding markers`);
