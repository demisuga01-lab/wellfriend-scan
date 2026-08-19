import type { ImageSize } from "../../../shared/src/scan-session.js";
export const browserImageLimits = { maxBytes: 25 * 1024 * 1024, maxPixels: 20_000_000, allowedTypes: new Set(["image/jpeg", "image/png", "image/webp"]) } as const;
export interface ImportedImage { readonly id: string; readonly blob: Blob; readonly objectUrl: string; readonly thumbnailUrl: string; readonly size: ImageSize; readonly mimeType: string; readonly source: "upload" | "camera" | "paste"; dispose(): void; }
export function validateBrowserImageMetadata(file: Pick<Blob, "size" | "type">, size?: ImageSize): void {
  if (!browserImageLimits.allowedTypes.has(file.type)) throw new Error("unsupported image type"); if (file.size <= 0 || file.size > browserImageLimits.maxBytes) throw new Error("image file exceeds the safe size limit"); if (size && size.width * size.height > browserImageLimits.maxPixels) throw new Error("image dimensions exceed the safe pixel limit");
}
export async function importBrowserImage(file: Blob, id: string, source: ImportedImage["source"]): Promise<ImportedImage> {
  validateBrowserImageMetadata(file); const bitmap = await createImageBitmap(file); const size = { width: bitmap.width, height: bitmap.height }; bitmap.close(); validateBrowserImageMetadata(file, size); const objectUrl = URL.createObjectURL(file); return { id, blob: file, objectUrl, thumbnailUrl: objectUrl, size, mimeType: file.type, source, dispose: () => URL.revokeObjectURL(objectUrl) };
}
export async function frameFromImportedImage(image: ImportedImage): Promise<ArrayBuffer> { return image.blob.arrayBuffer(); }
