import { access, readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import { join } from "node:path";

const roots = [
  { root: "web/public/wasm", kind: "wellfriend-wasm-package", required: ["wellfriend_perception_bg.wasm", "wellfriend_perception.js", "wellfriend_perception.d.ts", "package.json"] },
  { root: "android/app/src/main/assets/wellfriend-runtime/android", filesRoot: "android/app/src/main/jniLibs", kind: "wellfriend-android-abi", required: ["arm64-v8a/libwellfriend_perception.so", "arm64-v8a/libwellfriend_perception_jni.so", "x86_64/libwellfriend_perception.so", "x86_64/libwellfriend_perception_jni.so"] },
];
for (const entry of roots) {
  let manifestText;
  try { manifestText = await readFile(join(entry.root, "manifest.json"), "utf8"); } catch { console.log(`${entry.kind}: absent; product runtime remains fail-closed until a verified artifact is synced`); continue; }
  const manifest = JSON.parse(manifestText); const checksums = JSON.parse(await readFile(join(entry.root, "checksums.json"), "utf8"));
  if (manifest.schema_version !== 1 || manifest.artifact_kind !== entry.kind || !/^[0-9a-f]{40}$/.test(manifest.source_sha)) throw new Error(`invalid ${entry.kind} manifest`);
  const records = manifest.files ?? manifest.libraries; const sums = new Map(checksums.files.map((value) => [value.path, value.sha256]));
  for (const name of entry.required) {
    const record = records.find((value) => (value.path ?? value.file) === name); if (!record || !sums.has(name)) throw new Error(`missing ${entry.kind} file ${name}`);
    const bytes = await readFile(join(entry.filesRoot ?? entry.root, name)); const hash = createHash("sha256").update(bytes).digest("hex");
    if (hash !== record.sha256 || hash !== sums.get(name)) throw new Error(`checksum mismatch for ${entry.kind} ${name}`);
  }
  console.log(`${entry.kind}: verified ${manifest.source_sha}`);
}
