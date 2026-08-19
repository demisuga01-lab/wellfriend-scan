# Web camera

`WebCameraController` uses `navigator.mediaDevices.getUserMedia` with an environment-camera preference, shows a video preview, and extracts a JPEG through canvas only when requested. It reports permission/API/video readiness failures to the product shell. Browser camera access is not exercised in headless CI; the scanner controller is tested with deterministic frames.
