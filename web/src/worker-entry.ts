import { handleWorkerRequest } from "./perception-worker.js";
import type { WorkerRequest } from "./workers/protocol.js";
self.onmessage = (event: MessageEvent<WorkerRequest>) => { void handleWorkerRequest(event.data).then((response) => self.postMessage(response)); };
