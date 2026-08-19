import { addDraftPage, createSession, type ImageSize, type ScanSession } from "../../shared/src/scan-session.js";
import { debugSessionJson } from "../../web/src/export/debug-export.js";

/** Host-shell contract: Tauri/Electron/PWA adapters supply approved local-file metadata, never scanner algorithms. */
export interface DesktopFileDescriptor { readonly path: string; readonly uri: string; readonly size: ImageSize; readonly thumbnailUri?: string; }
export interface DesktopFolderInput { readonly folderUri: string; readonly files: readonly DesktopFileDescriptor[]; }
export class DesktopScannerWorkflow {
  private current: ScanSession;
  constructor(sessionId = `desktop-${Date.now()}`) { this.current = createSession(sessionId); }
  get session(): ScanSession { return this.current; }
  openFiles(files: readonly DesktopFileDescriptor[]): ScanSession { const startIndex = this.current.pages.length; files.forEach((file, index) => { if (!file.uri || !file.path) throw new Error("desktop file descriptor must be approved by the host shell"); this.current = addDraftPage(this.current, `desktop-${startIndex + index + 1}`, file.uri, file.size); }); return this.current; }
  openFolder(input: DesktopFolderInput): ScanSession { if (!input.folderUri) throw new Error("folder URI is required"); return this.openFiles(input.files); }
  exportDebugJson(): string { return debugSessionJson(this.current); }
}
