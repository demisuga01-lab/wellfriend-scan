import test from "node:test";
import assert from "node:assert/strict";
import { createSession, effectiveGeometry } from "../../dist/shared/src/scan-session.js";
import { DevMockPerceptionEngine } from "../../dist/web/src/perception/dev-mock-engine.js";
import { WebScanController } from "../../dist/web/src/scanner/controller.js";
import { exportDebugJson, validateExport } from "../../dist/web/src/export/debug-export.js";

const frame = { frameId: "frame", timestampMillis: 1, size: { width: 100, height: 200 }, rotationDegrees: 0, mimeType: "image/jpeg", bytes: new ArrayBuffer(2), source: "upload", mirrored: false };
test("web session drives mock analysis crop reconstruction filter and stable debug export", async () => {
  const engine = new DevMockPerceptionEngine(); const controller = new WebScanController(engine, createSession("web-session"));
  await controller.analyzeFrame(frame); assert.equal(controller.requestCapture("AUTO"), true);
  controller.addCapturedPage("page", "blob:page", { width: 1000, height: 2000 });
  controller.applyManualCrop("page", { corners: [{ x: 10, y: 10 }, { x: 990, y: 10 }, { x: 990, y: 1990 }, { x: 10, y: 1990 }], imageSize: { width: 1000, height: 2000 }, confidence: 1, source: "MANUAL" });
  await controller.reconstructPage("page"); await controller.applyFilter("page", "GRAYSCALE");
  assert.equal(effectiveGeometry(controller.state.session.pages[0]).source, "MANUAL"); assert.equal(controller.state.session.pages[0].filter, "GRAYSCALE");
  const result = exportDebugJson({ session: controller.state.session, format: "JSON_DEBUG" }); const json = JSON.parse(await result.blob.text()); assert.equal(json.schema_version, 1); assert.equal(json.session_id, "web-session"); assert.equal(json.pages[0].geometry_source, "MANUAL");
});
test("web export rejects an empty session", () => assert.throws(() => validateExport({ session: createSession("empty"), format: "JSON_DEBUG" }), /at least one/));
