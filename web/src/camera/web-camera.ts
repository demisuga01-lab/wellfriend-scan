/** Optional browser camera adapter. File import continues to work when unavailable. */
export class WebCameraController {
  private stream?: MediaStream;
  async start(video: HTMLVideoElement): Promise<void> { if (!navigator.mediaDevices?.getUserMedia) throw new Error("camera API is unavailable in this browser"); this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: "environment" } }, audio: false }); video.srcObject = this.stream; await video.play(); }
  capture(video: HTMLVideoElement): Promise<Blob> { const canvas = document.createElement("canvas"); canvas.width = video.videoWidth; canvas.height = video.videoHeight; if (!canvas.width || !canvas.height) return Promise.reject(new Error("camera video is not ready")); canvas.getContext("2d")?.drawImage(video, 0, 0); return new Promise((resolve, reject) => canvas.toBlob((blob) => blob ? resolve(blob) : reject(new Error("camera frame conversion failed")), "image/jpeg", .92)); }
  stop(): void { this.stream?.getTracks().forEach((track) => track.stop()); this.stream = undefined; }
}
