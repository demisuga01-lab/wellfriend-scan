# Perception worker

Worker messages are `ANALYZE_FRAME`, `RECONSTRUCT_PAGE`, `APPLY_FILTER`, `CANCEL_JOB`, and `PING`. Responses are typed analysis/reconstruction/filter results, `ERROR`, or `PONG`. Transfer ownership and cancellation queues can be added when a reviewed WASM runtime exists.

The worker mock is visibly marked `DEV_WEB_MOCK`; it creates test geometry solely to prove the product flow. It is not production detection, reconstruction, or filtering.
