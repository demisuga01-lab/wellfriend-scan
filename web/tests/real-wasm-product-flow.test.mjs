import test from "node:test";
import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import { resolve } from "node:path";
import { WasmPerceptionEngine } from "../../dist/web/src/perception/worker-engine.js";

const packageRoot = resolve("web/public/wasm");
let packageAvailable = true;
try {
  await access(resolve(packageRoot, "wellfriend_perception.js"));
  await access(resolve(packageRoot, "wellfriend_perception_bg.wasm"));
} catch {
  packageAvailable = false;
}

test("real packaged WASM performs analyze, reconstruction, and filter with registered decoded pixels", { skip: !packageAvailable && "run scripts/sync-wasm.ps1 before this real-package smoke" }, async () => {
  const module = await import(pathToFileURL(resolve(packageRoot, "wellfriend_perception.js")).href);
  module.initSync({ module: await readFile(resolve(packageRoot, "wellfriend_perception_bg.wasm")) });
  const source = new Uint8Array(64);
  for (let y = 1; y < 7; y += 1) for (let x = 1; x < 7; x += 1) source[y * 8 + x] = 240;
  const images = new Map([["fixture://source", { width: 8, height: 8, stride: 8, pixelFormat: "Gray8", bytes: source }]]);
  const resolver = {
    resolve: async (uri) => {
      const image = images.get(uri);
      if (!image) throw new Error(`unregistered runtime image: ${uri}`);
      return image;
    },
    register: (uri, image) => images.set(uri, image),
  };
  const engine = WasmPerceptionEngine.fromModule(module, resolver);
  const analysis = await engine.analyzeFrame({ frameId: "1", timestampMillis: 1, size: { width: 8, height: 8 }, rotationDegrees: 0, mimeType: "application/octet-stream", bytes: source.buffer, source: "upload", mirrored: false });
  assert.equal(analysis.engineMode, "WASM");
  const geometry = { corners: [{ x: 1, y: 1 }, { x: 6, y: 1 }, { x: 6, y: 6 }, { x: 1, y: 6 }], imageSize: { width: 8, height: 8 }, confidence: 1, source: "MANUAL" };
  const reconstructed = await engine.reconstructPage({ pageId: "fixture", sourceUri: "fixture://source", sourceSize: { width: 8, height: 8 }, geometry, outputLongEdge: 256, aspectPolicy: "free_from_quad", orientationPolicy: "preserve_source", cropMarginPolicy: "safe_inner" });
  assert.equal(reconstructed.engineMode, "WASM");
  assert.ok(reconstructed.outputSize.width >= 1 && reconstructed.outputSize.height >= 1);
  const filtered = await engine.applyFilter({ pageId: "fixture", inputUri: reconstructed.outputUri, preset: "GRAYSCALE", conditionVector: { conditions: {} } });
  assert.equal(filtered.engineMode, "WASM");
  assert.ok(images.has(reconstructed.outputUri), "reconstructed pixels remain worker-local for the filter request");
});
