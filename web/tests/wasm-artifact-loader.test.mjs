import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { validateWasmArtifactManifest } from "../../dist/web/src/perception/wasm-artifact-loader.js";

test("WASM artifact manifest enforces the reviewed runtime package contract", async () => {
  const fixture = JSON.parse(await readFile("web/tests/fixtures/wasm-manifest.json", "utf8"));
  assert.equal(validateWasmArtifactManifest(fixture).source_sha, "a".repeat(40));
  assert.throws(() => validateWasmArtifactManifest({ ...fixture, source_sha: "unsafe" }));
  assert.throws(() => validateWasmArtifactManifest({ ...fixture, files: fixture.files.slice(1) }));
});
