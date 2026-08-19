import test from "node:test";
import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { resolve } from "node:path";

const packageRoot = resolve("web/public/wasm");
let packageAvailable = true;
try { await access(resolve(packageRoot, "wellfriend_perception.js")); await access(resolve(packageRoot, "wellfriend_perception_bg.wasm")); } catch { packageAvailable = false; }

test("synced browser WASM package runs scalar analyze smoke", { skip: !packageAvailable && "run scripts/sync-wasm.ps1 before this real-package smoke" }, async () => {
  const module = await import(pathToFileURL(resolve(packageRoot, "wellfriend_perception.js")).href);
  module.initSync({ module: await readFile(resolve(packageRoot, "wellfriend_perception_bg.wasm")) });
  const engine = module.createEngine("{}");
  const response = JSON.parse(engine.analyzeFrame(new Uint8Array(64), 8, 8, 8, "Gray8", "{}"));
  assert.equal(response.schema_version, 1);
  assert.equal(response.engine_mode, "native_scalar");
});
