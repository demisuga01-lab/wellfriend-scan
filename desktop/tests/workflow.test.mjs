import test from "node:test";
import assert from "node:assert/strict";
import { DesktopScannerWorkflow } from "../../dist/desktop/src/workflow.js";

test("desktop folder workflow creates a platform-neutral batch session and debug export", () => {
  const workflow = new DesktopScannerWorkflow("desktop-test");
  workflow.openFolder({ folderUri: "file:///safe/folder", files: [
    { path: "safe/a.jpg", uri: "file:///safe/a.jpg", size: { width: 100, height: 200 } },
    { path: "safe/b.png", uri: "file:///safe/b.png", size: { width: 200, height: 100 } },
  ] });
  assert.equal(workflow.session.pages.length, 2);
  assert.deepEqual(workflow.session.pages.map((page) => page.id), ["desktop-1", "desktop-2"]);
  assert.match(workflow.exportDebugJson(), /desktop-test/);
});
