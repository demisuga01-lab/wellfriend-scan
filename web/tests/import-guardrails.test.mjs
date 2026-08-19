import test from "node:test";
import assert from "node:assert/strict";
import { browserImageLimits, validateBrowserImageMetadata } from "../../dist/web/src/import/browser-image.js";

test("browser import guardrails reject unknown MIME types, oversized files, and oversized decodes", () => {
  assert.throws(() => validateBrowserImageMetadata({ type: "application/pdf", size: 1 }), /unsupported/);
  assert.throws(() => validateBrowserImageMetadata({ type: "image/jpeg", size: browserImageLimits.maxBytes + 1 }), /safe size/);
  assert.throws(() => validateBrowserImageMetadata({ type: "image/png", size: 1 }, { width: 5000, height: 5000 }), /pixel/);
  assert.doesNotThrow(() => validateBrowserImageMetadata({ type: "image/webp", size: 1 }, { width: 100, height: 100 }));
});
