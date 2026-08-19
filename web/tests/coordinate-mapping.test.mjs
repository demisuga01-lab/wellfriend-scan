import test from "node:test";
import assert from "node:assert/strict";
import { CanvasCoordinateMapper } from "../../dist/web/src/geometry/coordinate-mapping.js";

test("canvas mapping round-trips fit, fill, rotation, CSS scale, and mirroring", () => {
  for (const rotation of [0, 90, 180, 270]) {
    const mapper = new CanvasCoordinateMapper({ width: 400, height: 200 }, { width: 300, height: 300, devicePixelRatio: 2 }, rotation, "FIT", false);
    const point = { x: 80, y: 50 }; const recovered = mapper.canvasToImage(mapper.imageToCanvas(point));
    assert.ok(Math.abs(point.x - recovered.x) < .001); assert.ok(Math.abs(point.y - recovered.y) < .001);
  }
  const mirrored = new CanvasCoordinateMapper({ width: 400, height: 200 }, { width: 300, height: 300, devicePixelRatio: 1 }, 0, "FILL", true);
  assert.ok(mirrored.imageToCanvas({ x: 0, y: 0 }).x > mirrored.imageToCanvas({ x: 400, y: 0 }).x);
});
