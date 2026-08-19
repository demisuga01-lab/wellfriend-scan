# Boundary detection policy

Wellfriend targets best-available, shape-general boundaries rather than a false “perfect edge detection” guarantee. A strong visible edge may produce a high-confidence quad, polygon, circle, ellipse, contour, or future mask. Weak, ambiguous, occluded, saturated, low-contrast, or out-of-frame evidence must remain visible as diagnostics and may require manual correction.

The MP10 document path is a scalar quad baseline. It never fabricates an unseen page edge: it reports insufficient evidence and lets the validated manual crop path supply user evidence. Future arbitrary-shape support is a runtime contract, not a claim that segmentation has shipped.
