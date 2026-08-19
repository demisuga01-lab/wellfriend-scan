import { access, readFile } from "node:fs/promises";

const required = [
  "web/public/index.html",
  "web/src/app.ts",
  "web/src/scanner/controller.ts",
  "web/src/perception/contracts.ts",
  "web/src/perception/worker-engine.ts",
  "web/src/perception-worker.ts",
  "web/src/geometry/coordinate-mapping.ts",
  "web/src/import/browser-image.ts",
  "web/src/export/debug-export.ts",
  "desktop/src/workflow.ts",
  "shared/docs/contracts.md",
];
await Promise.all(required.map((path) => access(path)));
const source = Buffer.concat(await Promise.all(required.slice(1, 10).map((path) => readFile(path)))).toString("utf8");
for (const marker of ["WebPerceptionEngine", "WorkerPerceptionEngine", "WasmPerceptionEngine", "ANALYZE_FRAME", "RECONSTRUCT_PAGE", "APPLY_FILTER", "CanvasCoordinateMapper", "JSON_DEBUG"]) {
  if (!source.includes(marker)) throw new Error(`web/desktop scanner contract is missing ${marker}`);
}
if (/mlkit|google\.ml\.kit/i.test(source)) throw new Error("ML Kit must not become a required web/desktop dependency");
console.log(`Web/desktop scanner contract passed for ${required.length} required files and binding markers`);
