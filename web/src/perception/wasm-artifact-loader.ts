import type { WasmRuntimeModule } from "./worker-engine.js";

export interface WasmArtifactManifestFile { readonly path: string; readonly sha256: string; readonly bytes: number; }
export interface WasmArtifactManifest {
  readonly schema_version: number;
  readonly artifact_kind: string;
  readonly source_sha: string;
  readonly runtime_schema_version: number;
  readonly exports: readonly string[];
  readonly files: readonly WasmArtifactManifestFile[];
}

const REQUIRED_FILES = ["wellfriend_perception_bg.wasm", "wellfriend_perception.js", "wellfriend_perception.d.ts", "package.json"] as const;
const REQUIRED_EXPORTS = ["createEngine", "destroyEngine", "version", "EngineHandle.analyzeFrame", "EngineHandle.reconstructPage", "EngineHandle.applyFilter"] as const;

/** Validates the local package manifest before any generated loader is imported. */
export function validateWasmArtifactManifest(value: unknown): WasmArtifactManifest {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("invalid WASM artifact manifest");
  const manifest = value as Record<string, unknown>;
  if (manifest.schema_version !== 1 || manifest.artifact_kind !== "wellfriend-wasm-package") throw new Error("unsupported WASM artifact manifest");
  if (typeof manifest.source_sha !== "string" || !/^[0-9a-f]{40}$/.test(manifest.source_sha)) throw new Error("invalid WASM artifact source SHA");
  const exports = manifest.exports;
  if (manifest.runtime_schema_version !== 1 || !Array.isArray(exports) || exports.some((entry) => typeof entry !== "string") || REQUIRED_EXPORTS.some((entry) => !exports.includes(entry))) throw new Error("WASM artifact exports do not match the runtime contract");
  if (!Array.isArray(manifest.files)) throw new Error("WASM artifact manifest files are missing");
  const files = manifest.files.map((entry) => {
    if (!entry || typeof entry !== "object" || Array.isArray(entry)) throw new Error("invalid WASM artifact file entry");
    const file = entry as Record<string, unknown>;
    if (typeof file.path !== "string" || file.path.includes("..") || file.path.includes("\\") || typeof file.sha256 !== "string" || !/^[0-9a-f]{64}$/.test(file.sha256) || !Number.isInteger(file.bytes) || (file.bytes as number) < 1) throw new Error("invalid WASM artifact file metadata");
    return { path: file.path, sha256: file.sha256, bytes: file.bytes as number };
  });
  if (REQUIRED_FILES.some((entry) => !files.some((file) => file.path === entry))) throw new Error("WASM artifact is incomplete");
  return { schema_version: 1, artifact_kind: "wellfriend-wasm-package", source_sha: manifest.source_sha, runtime_schema_version: 1, exports, files };
}

function join(base: string, name: string): string { return `${base.replace(/\/$/, "")}/${name}`; }
async function sha256(bytes: ArrayBuffer): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, "0")).join("");
}
async function fetchChecked(url: string, file: WasmArtifactManifestFile): Promise<ArrayBuffer> {
  const response = await fetch(url, { cache: "no-store", credentials: "same-origin" });
  if (!response.ok) throw new Error(`WASM artifact fetch failed: ${file.path}`);
  const bytes = await response.arrayBuffer();
  if (bytes.byteLength !== file.bytes || await sha256(bytes) !== file.sha256) throw new Error(`WASM artifact checksum mismatch: ${file.path}`);
  return bytes;
}

/** Loads only a local, manifest-verified wasm-bindgen package; no dev mock fallback exists here. */
export async function loadVerifiedWasmRuntime(baseUrl = "/wasm"): Promise<WasmRuntimeModule> {
  const manifestResponse = await fetch(join(baseUrl, "manifest.json"), { cache: "no-store", credentials: "same-origin" });
  if (!manifestResponse.ok) throw new Error("reviewed Wellfriend WASM manifest is unavailable");
  const manifest = validateWasmArtifactManifest(await manifestResponse.json());
  const files = new Map(manifest.files.map((file) => [file.path, file]));
  const [wasm, loader] = await Promise.all([
    fetchChecked(join(baseUrl, "wellfriend_perception_bg.wasm"), files.get("wellfriend_perception_bg.wasm")!),
    fetchChecked(join(baseUrl, "wellfriend_perception.js"), files.get("wellfriend_perception.js")!),
    fetchChecked(join(baseUrl, "wellfriend_perception.d.ts"), files.get("wellfriend_perception.d.ts")!),
    fetchChecked(join(baseUrl, "package.json"), files.get("package.json")!),
  ]).then((entries) => [entries[0], entries[1]] as const);
  // The generated ESM loader is imported only after its local artifact checksum is verified.
  void loader;
  const imported = await import(/* @vite-ignore */ join(baseUrl, "wellfriend_perception.js")) as unknown as {
    initSync?: (bytes: ArrayBuffer) => unknown;
    createEngine?: (configJson?: string) => unknown;
  };
  if (typeof imported.initSync !== "function" || typeof imported.createEngine !== "function") throw new Error("WASM loader exports do not match the runtime contract");
  imported.initSync(wasm);
  return { createEngine: imported.createEngine as WasmRuntimeModule["createEngine"] };
}
