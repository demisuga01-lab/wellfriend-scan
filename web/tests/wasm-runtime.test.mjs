import test from "node:test";
import assert from "node:assert/strict";
import { WasmPerceptionEngine } from "../../dist/web/src/perception/worker-engine.js";
import { handleWorkerRequest } from "../../dist/web/src/perception-worker.js";

const analysis = JSON.stringify({
  schema_version: 1, engine: "wellfriend-perception", engine_mode: "native_scalar",
  quality: { blur_laplacian_variance: { raw_value: 10, normalized_score: .5, confidence: .7 } },
  candidates: [{ points: [{ x: 1, y: 1 }, { x: 6, y: 1 }, { x: 6, y: 6 }, { x: 1, y: 6 }] }],
  fused_quad: { points: [{ x: 1, y: 1 }, { x: 6, y: 1 }, { x: 6, y: 6 }, { x: 1, y: 6 }] },
  refined_quad: null, boundary: { kind: "quad" }, capture_readiness: "READY", capture_readiness_score: .8,
  guidance: ["READY"], diagnostics: ["scalar fixture"], timings_micros: {},
});
const module = { createEngine: () => ({ analyzeFrame: () => analysis, reconstructPage: () => { throw new Error("not used"); }, applyFilter: () => { throw new Error("not used"); } }) };
const frame = { frameId: "3", timestampMillis: 1, size: { width: 8, height: 8 }, rotationDegrees: 0, mimeType: "application/octet-stream", bytes: new Uint8Array(64).buffer, source: "upload", mirrored: false };

test("WASM adapter maps reviewed Rust runtime JSON without a TypeScript detector", async () => {
  const engine = WasmPerceptionEngine.fromModule(module);
  const result = await engine.analyzeFrame(frame);
  assert.equal(result.engineMode, "WASM");
  assert.equal(result.fusionResult.geometry.corners[0].x, 1);
  assert.equal(result.guidance[0], "READY");
});

test("worker exposes explicit WASM load failure and readiness states", async () => {
  const failed = await handleWorkerRequest({ requestId: "load-failed", type: "LOAD_WASM" });
  assert.equal(failed.type, "ENGINE_FAILED");
  const nativeLike = { mode: "WASM", analyzeFrame: async () => { throw new Error("not used"); }, reconstructPage: async () => { throw new Error("not used"); }, applyFilter: async () => { throw new Error("not used"); } };
  assert.deepEqual(await handleWorkerRequest({ requestId: "load", type: "LOAD_WASM" }, nativeLike), { requestId: "load", type: "ENGINE_READY" });
});
