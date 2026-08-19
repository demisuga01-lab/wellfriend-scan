import test from "node:test";
import assert from "node:assert/strict";
import { addDraftPage, attachGeometry, createSession, deletePage, reorderPage, rotatePage, validateGeometry } from "../../dist/shared/src/scan-session.js";

test("platform-neutral session mutations mirror Android session semantics", () => {
  let session = createSession("shared"); session = addDraftPage(session, "a", "blob:a", { width: 100, height: 200 }); session = addDraftPage(session, "b", "blob:b", { width: 100, height: 200 });
  session = reorderPage(session, 1, 0); assert.deepEqual(session.pages.map((page) => page.id), ["b", "a"]); session = rotatePage(session, "b"); assert.equal(session.pages[0].rotationDegrees, 90); session = deletePage(session, "b"); assert.deepEqual(session.pages.map((page) => page.id), ["a"]);
  const geometry = { corners: [{ x: 5, y: 5 }, { x: 95, y: 5 }, { x: 95, y: 195 }, { x: 5, y: 195 }], imageSize: { width: 100, height: 200 }, confidence: .9, source: "MANUAL" };
  assert.equal(attachGeometry(session, "a", geometry).pages[0].manualGeometry.source, "MANUAL"); assert.throws(() => validateGeometry({ ...geometry, confidence: 2 }), /confidence/);
});
