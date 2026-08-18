import test from "node:test";
import assert from "node:assert/strict";
import { handlePerceptionRequest } from "../../dist/web/src/perception-worker.js";

test("worker reports missing WASM instead of fabricating an output", () => {
  const result = handlePerceptionRequest({ requestId: "r1", domain: "document", input: { kind: "image", byteLength: 42 } });
  assert.equal(result.status, "unsupported");
  assert.match(result.diagnostics[0], /not installed/);
});

