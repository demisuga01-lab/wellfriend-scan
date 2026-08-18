/** Shared product state that maps cleanly to a perception-core run. */
export type PageState = "draft" | "capturing" | "reconstructing" | "ready" | "failed";
export type ProcessingStatus = "idle" | "queued" | "running" | "complete" | "failed";
export type FilterPreset = "original" | "document" | "grayscale" | "high-contrast";

export interface PageGeometry {
  readonly corners: readonly [number, number][];
  readonly confidence: number;
  readonly source: "auto" | "manual";
}

export interface ScanPage {
  readonly id: string;
  readonly state: PageState;
  readonly geometry?: PageGeometry;
  readonly filter: FilterPreset;
  readonly status: ProcessingStatus;
}

export interface ExportOptions {
  readonly format: "pdf" | "images" | "structured";
  readonly includeOcr: boolean;
  readonly quality: "balanced" | "high";
}

export interface ScanSession {
  readonly id: string;
  readonly domain: "document";
  readonly pages: readonly ScanPage[];
  readonly exportOptions: ExportOptions;
}

export function addDraftPage(session: ScanSession, pageId: string): ScanSession {
  return { ...session, pages: [...session.pages, { id: pageId, state: "draft", filter: "original", status: "idle" }] };
}

export function attachGeometry(session: ScanSession, pageId: string, geometry: PageGeometry): ScanSession {
  return { ...session, pages: session.pages.map((page) => page.id === pageId ? { ...page, geometry, state: "reconstructing", status: "queued" } : page) };
}

