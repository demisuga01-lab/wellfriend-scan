import test from "node:test";
import assert from "node:assert/strict";
import { addDraftPage } from "../dist/shared/src/scan-session.js";
import { handleWorkerRequest } from "../dist/web/src/perception-worker.js";

test("document flow has an explicit core-to-export boundary", async () => {
  const session = addDraftPage({ id: "session", domain: "document", pages: [], exportOptions: { format: "PDF_PLACEHOLDER", includeOcr: false, quality: "high" } }, "page");
  const perception = await handleWorkerRequest({ requestId: session.pages[0].id, type: "PING" });
  assert.equal(perception.type, "PONG");
  assert.equal(session.exportOptions.format, "PDF_PLACEHOLDER");
});
