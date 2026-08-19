/**
 * Platform-neutral scanner DTOs. String values deliberately match the MP7
 * Kotlin contracts and are the future Rust/WASM wire values.
 */
export type ScannerState =
  | "IDLE" | "REQUESTING_PERMISSION" | "CAMERA_STARTING" | "SEARCHING_FOR_DOCUMENT"
  | "DOCUMENT_CANDIDATE_FOUND" | "ALMOST_READY" | "READY" | "CAPTURING" | "CAPTURED"
  | "REVIEWING_PAGE" | "EDITING_CROP" | "APPLYING_FILTER" | "PAGE_ACCEPTED" | "EXPORTING" | "ERROR";
export type CaptureMode = "AUTO" | "MANUAL" | "BATCH" | "GALLERY";
export type CaptureGuidance = "NO_DOCUMENT" | "MOVE_CLOSER" | "MOVE_FARTHER" | "HOLD_STEADY"
  | "TOO_DARK" | "TOO_BRIGHT" | "TOO_BLURRY" | "GLARE_DETECTED" | "DOCUMENT_CUT_OFF"
  | "LOW_CONFIDENCE" | "LOW_DETECTOR_AGREEMENT" | "READY" | "CAPTURING";
export type CaptureReadiness = "NOT_READY" | "ALMOST_READY" | "READY" | "CAPTURE_NOW";
export type PageState = "DRAFT" | "CAPTURING" | "RECONSTRUCTING" | "READY" | "FAILED";
export type ProcessingStatus = "IDLE" | "QUEUED" | "RUNNING" | "COMPLETE" | "FAILED";
export type FilterPreset = "ORIGINAL" | "AUTO" | "CLEAN" | "COLOR" | "GRAYSCALE" | "BLACK_AND_WHITE"
  | "RECEIPT" | "BOOK" | "WHITEBOARD" | "PHOTO_DOCUMENT";
export type ExportFormat = "JPEG" | "PNG" | "PDF_PLACEHOLDER" | "JSON_DEBUG";
export type GeometrySource = "AUTO" | "MANUAL";

export interface ImageSize { readonly width: number; readonly height: number; }
export interface Point2D { readonly x: number; readonly y: number; }
export interface PageGeometry {
  readonly corners: readonly [Point2D, Point2D, Point2D, Point2D];
  readonly imageSize: ImageSize;
  readonly confidence: number;
  readonly source: GeometrySource;
}
export interface CanonicalPagePreview { readonly uri: string; readonly size: ImageSize; readonly diagnostics: readonly string[]; }
export interface ScanPage {
  readonly id: string;
  readonly sourceUri: string;
  readonly sourceSize: ImageSize;
  readonly thumbnailUri?: string;
  readonly detectedGeometry?: PageGeometry;
  readonly manualGeometry?: PageGeometry;
  readonly canonicalPage?: CanonicalPagePreview;
  readonly filter: FilterPreset;
  readonly rotationDegrees: 0 | 90 | 180 | 270;
  readonly state: PageState;
  readonly processingStatus: ProcessingStatus;
  readonly diagnostics: readonly string[];
}
export interface ExportOptions { readonly format: ExportFormat; readonly includeOcr: boolean; readonly quality: "balanced" | "high"; }
export interface ScanSession { readonly id: string; readonly domain: "document"; readonly pages: readonly ScanPage[]; readonly exportOptions: ExportOptions; }

export const defaultExportOptions = (): ExportOptions => ({ format: "PDF_PLACEHOLDER", includeOcr: false, quality: "balanced" });
export const createSession = (id: string): ScanSession => ({ id, domain: "document", pages: [], exportOptions: defaultExportOptions() });

export function validateImageSize(size: ImageSize): void {
  if (!Number.isInteger(size.width) || !Number.isInteger(size.height) || size.width <= 0 || size.height <= 0) throw new Error("image dimensions must be positive integers");
}
function signedArea(points: readonly Point2D[]): number { return points.reduce((area, point, index) => { const next = points[(index + 1) % points.length]; return area + point.x * next.y - next.x * point.y; }, 0) / 2; }
function isConvex(points: readonly Point2D[]): boolean {
  const cross = points.map((a, index) => { const b = points[(index + 1) % 4]; const c = points[(index + 2) % 4]; return (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x); });
  return cross.every((value) => value > 0) || cross.every((value) => value < 0);
}
export function validateGeometry(geometry: PageGeometry): PageGeometry {
  validateImageSize(geometry.imageSize);
  if (!Number.isFinite(geometry.confidence) || geometry.confidence < 0 || geometry.confidence > 1) throw new Error("geometry confidence must be bounded");
  if (geometry.corners.length !== 4 || geometry.corners.some((point) => !Number.isFinite(point.x) || !Number.isFinite(point.y) || point.x < 0 || point.y < 0 || point.x > geometry.imageSize.width || point.y > geometry.imageSize.height)) throw new Error("page corners must be finite and inside the image");
  if (Math.abs(signedArea(geometry.corners)) <= 1 || !isConvex(geometry.corners)) throw new Error("page geometry must be convex and non-zero");
  return geometry;
}

export function addDraftPage(session: ScanSession, pageId: string, sourceUri = `memory://${pageId}`, sourceSize: ImageSize = { width: 1, height: 1 }): ScanSession {
  validateImageSize(sourceSize);
  if (session.pages.some((page) => page.id === pageId)) throw new Error("page id already exists");
  const page: ScanPage = { id: pageId, sourceUri, sourceSize, filter: "ORIGINAL", rotationDegrees: 0, state: "DRAFT", processingStatus: "IDLE", diagnostics: [] };
  return { ...session, pages: [...session.pages, page] };
}
export function pageById(session: ScanSession, pageId: string): ScanPage { const page = session.pages.find((candidate) => candidate.id === pageId); if (!page) throw new Error("page does not exist"); return page; }
export function updatePage(session: ScanSession, page: ScanPage): ScanSession { pageById(session, page.id); return { ...session, pages: session.pages.map((item) => item.id === page.id ? page : item) }; }
export function attachGeometry(session: ScanSession, pageId: string, geometry: PageGeometry): ScanSession { const page = pageById(session, pageId); return updatePage(session, { ...page, manualGeometry: geometry.source === "MANUAL" ? validateGeometry(geometry) : page.manualGeometry, detectedGeometry: geometry.source === "AUTO" ? validateGeometry(geometry) : page.detectedGeometry, state: "RECONSTRUCTING", processingStatus: "QUEUED" }); }
export function deletePage(session: ScanSession, pageId: string): ScanSession { pageById(session, pageId); return { ...session, pages: session.pages.filter((page) => page.id !== pageId) }; }
export function reorderPage(session: ScanSession, from: number, to: number): ScanSession { if (!Number.isInteger(from) || !Number.isInteger(to) || from < 0 || to < 0 || from >= session.pages.length || to >= session.pages.length) throw new Error("page index out of bounds"); const pages = [...session.pages]; const [page] = pages.splice(from, 1); pages.splice(to, 0, page); return { ...session, pages }; }
export function rotatePage(session: ScanSession, pageId: string): ScanSession { const page = pageById(session, pageId); const rotationDegrees = ((page.rotationDegrees + 90) % 360) as ScanPage["rotationDegrees"]; return updatePage(session, { ...page, rotationDegrees }); }
export function effectiveGeometry(page: ScanPage): PageGeometry | undefined { return page.manualGeometry ?? page.detectedGeometry; }
