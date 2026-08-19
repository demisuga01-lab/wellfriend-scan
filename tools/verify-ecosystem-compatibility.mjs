import { readFile } from "node:fs/promises";
const manifest = JSON.parse(await readFile("docs/ecosystem-compatibility.json", "utf8"));
if (manifest.schema_version !== 1 || manifest.ecosystem_version !== "0.1.0-alpha.1") throw new Error("invalid ecosystem compatibility schema/version");
for (const field of ["device_classes", "document_tasks", "filter_presets", "processor_ids", "guidance_codes", "export_formats"]) if (!manifest.shared_contracts[field]?.length) throw new Error(`compatibility field ${field} is empty`);
if (!manifest.known_blockers?.length || !manifest.mock_boundaries?.length) throw new Error("release blockers and mock boundaries must be disclosed");
console.log("Ecosystem compatibility manifest passed");
