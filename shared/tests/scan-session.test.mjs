import test from "node:test";
import assert from "node:assert/strict";
import { addDraftPage, attachGeometry } from "../../dist/shared/src/scan-session.js";

test("shared scanner page state maps to a perception request", () => {
  const session = addDraftPage({ id: "session-1", domain: "document", pages: [], exportOptions: { format: "pdf", includeOcr: true, quality: "balanced" } }, "page-1");
  const updated = attachGeometry(session, "page-1", { corners: [[0, 0], [1, 0], [1, 1], [0, 1]], confidence: 0.9, source: "auto" });
  assert.equal(updated.pages[0].state, "reconstructing");
  assert.equal(updated.pages[0].status, "queued");
});

