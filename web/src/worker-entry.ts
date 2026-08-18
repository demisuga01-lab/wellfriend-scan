import { handlePerceptionRequest, type PerceptionRequest } from "./perception-worker.js";

self.onmessage = (event: MessageEvent<PerceptionRequest>) => {
  self.postMessage(handlePerceptionRequest(event.data));
};

