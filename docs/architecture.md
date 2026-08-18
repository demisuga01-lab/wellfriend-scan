# Product architecture

`shared` owns product-state concepts. Android and web own capture/UI lifecycle and pass observations to a `wellfriend-perception` binding. The perception engine returns reconstruction and semantic outputs; the product owns review, manual correction, multi-page session state, and export choices. `wellfriend-models` is the only source of validated model artifacts.

