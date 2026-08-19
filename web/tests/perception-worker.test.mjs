import test from "node:test";
import assert from "node:assert/strict";
import { handleWorkerRequest } from "../../dist/web/src/perception-worker.js";

const frame = { frameId: "frame", timestampMillis: 1, size: { width: 100, height: 200 }, rotationDegrees: 0, mimeType: "image/jpeg", bytes: new ArrayBuffer(2), source: "upload", mirrored: false };
test("worker protocol provides ping and dev-only analysis responses", async () => {
  assert.deepEqual(await handleWorkerRequest({ requestId: "ping", type: "PING" }), { requestId: "ping", type: "PONG" });
  const response = await handleWorkerRequest({ requestId: "analysis", type: "ANALYZE_FRAME", frame });
  assert.equal(response.type, "ANALYSIS_RESULT");
  assert.equal(response.result.engineMode, "DEV_WEB_MOCK");
  assert.match(response.result.diagnostics[0], /not production/);
});
