import test from "node:test";
import assert from "node:assert/strict";
import { addDraftPage, attachGeometry } from "../../dist/shared/src/scan-session.js";

test("shared scanner page state maps to a perception request", () => {
  const session = addDraftPage({ id: "session-1", domain: "document", pages: [], exportOptions: { format: "PDF_PLACEHOLDER", includeOcr: true, quality: "balanced" } }, "page-1", "memory://page", { width: 10, height: 10 });
  const updated = attachGeometry(session, "page-1", { corners: [{ x: 1, y: 1 }, { x: 9, y: 1 }, { x: 9, y: 9 }, { x: 1, y: 9 }], imageSize: { width: 10, height: 10 }, confidence: 0.9, source: "AUTO" });
  assert.equal(updated.pages[0].state, "RECONSTRUCTING");
  assert.equal(updated.pages[0].processingStatus, "QUEUED");
});
