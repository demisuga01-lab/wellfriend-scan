import test from "node:test";
import assert from "node:assert/strict";
import { ObjectUrlRegistry } from "../../dist/web/src/import/object-url-registry.js";

test("object URL lifecycle revokes replaced, released, and remaining URLs", () => {
  const released = []; const registry = new ObjectUrlRegistry((url) => released.push(url));
  registry.track("page", "blob:first"); registry.track("page", "blob:second"); registry.track("other", "blob:other"); registry.release("page"); registry.dispose();
  assert.deepEqual(released, ["blob:first", "blob:second", "blob:other"]);
});
