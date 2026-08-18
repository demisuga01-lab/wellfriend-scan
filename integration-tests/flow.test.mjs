import test from "node:test";
import assert from "node:assert/strict";
import { addDraftPage } from "../dist/shared/src/scan-session.js";
import { handlePerceptionRequest } from "../dist/web/src/perception-worker.js";

test("document flow has an explicit core-to-export boundary", () => {
  const session = addDraftPage({ id: "session", domain: "document", pages: [], exportOptions: { format: "pdf", includeOcr: false, quality: "high" } }, "page");
  const perception = handlePerceptionRequest({ requestId: session.pages[0].id, domain: "document", input: { kind: "image", byteLength: 1 } });
  assert.equal(perception.status, "unsupported");
  assert.equal(session.exportOptions.format, "pdf");
});

