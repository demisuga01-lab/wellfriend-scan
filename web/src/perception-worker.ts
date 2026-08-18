/** Message protocol for the future WASM binding to wellfriend-perception. */
export interface PerceptionRequest {
  readonly requestId: string;
  readonly domain: "document";
  readonly input: { readonly kind: "image"; readonly byteLength: number };
}

export interface PerceptionResponse {
  readonly requestId: string;
  readonly status: "unsupported" | "complete" | "failed";
  readonly diagnostics: readonly string[];
}

/**
 * MP1 has no WASM binary. The explicit unsupported response prevents the web
 * product from pretending it performed page reconstruction.
 */
export function handlePerceptionRequest(request: PerceptionRequest): PerceptionResponse {
  return { requestId: request.requestId, status: "unsupported", diagnostics: ["WASM perception binding is not installed in MP1"] };
}

