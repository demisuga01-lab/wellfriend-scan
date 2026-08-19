import type { ImageSize } from "../../../shared/src/scan-session.js";
export const browserImageLimits = { maxBytes: 25 * 1024 * 1024, maxPixels: 20_000_000, allowedTypes: new Set(["image/jpeg", "image/png", "image/webp"]) } as const;
export interface ImportedImage { readonly id: string; readonly blob: Blob; readonly objectUrl: string; readonly thumbnailUrl: string; readonly size: ImageSize; readonly mimeType: string; readonly source: "upload" | "camera" | "paste"; dispose(): void; }
export interface DecodedBrowserFrame { readonly bytes: ArrayBuffer; readonly size: ImageSize; readonly pixelFormat: "Rgba8"; readonly rowStrideBytes: number; }
export function validateBrowserImageMetadata(file: Pick<Blob, "size" | "type">, size?: ImageSize): void {
  if (!browserImageLimits.allowedTypes.has(file.type)) throw new Error("unsupported image type"); if (file.size <= 0 || file.size > browserImageLimits.maxBytes) throw new Error("image file exceeds the safe size limit"); if (size && size.width * size.height > browserImageLimits.maxPixels) throw new Error("image dimensions exceed the safe pixel limit");
}
export async function importBrowserImage(file: Blob, id: string, source: ImportedImage["source"]): Promise<ImportedImage> {
  validateBrowserImageMetadata(file); const bitmap = await createImageBitmap(file); const size = { width: bitmap.width, height: bitmap.height }; bitmap.close(); validateBrowserImageMetadata(file, size); const objectUrl = URL.createObjectURL(file); return { id, blob: file, objectUrl, thumbnailUrl: objectUrl, size, mimeType: file.type, source, dispose: () => URL.revokeObjectURL(objectUrl) };
}
/** Decodes browser image bytes before transport; Rust never receives encoded JPEG/PNG/WebP bytes as pixels. */
export async function frameFromImportedImage(image: ImportedImage): Promise<DecodedBrowserFrame> {
  const bitmap = await createImageBitmap(image.blob);
  try {
    if (bitmap.width !== image.size.width || bitmap.height !== image.size.height) throw new Error("decoded image dimensions changed unexpectedly");
    const canvas = document.createElement("canvas"); canvas.width = bitmap.width; canvas.height = bitmap.height;
    const context = canvas.getContext("2d", { willReadFrequently: true }); if (!context) throw new Error("browser canvas decoder is unavailable");
    context.drawImage(bitmap, 0, 0); const pixels = context.getImageData(0, 0, bitmap.width, bitmap.height).data;
    return { bytes: pixels.buffer.slice(pixels.byteOffset, pixels.byteOffset + pixels.byteLength), size: image.size, pixelFormat: "Rgba8", rowStrideBytes: bitmap.width * 4 };
  } finally { bitmap.close(); }
}
