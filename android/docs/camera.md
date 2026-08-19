# Camera and sensor flow

`CameraXScannerController` binds Preview, ImageAnalysis, and ImageCapture to the activity lifecycle. Analysis uses `STRATEGY_KEEP_ONLY_LATEST`, copies a compact luma frame, closes `ImageProxy`, and then invokes suspend perception work off the analyzer executor. ImageCapture writes a maximum-quality image to disk; pages retain a URI and thumbnail reference rather than bitmap bytes.

Torch, linear zoom, focus/metering, exposure compensation, rotation metadata, front/back selection, and an accelerometer hook are present. IMU samples are only an MP3 temporal-input seam; capture readiness remains the perception engine's decision.
