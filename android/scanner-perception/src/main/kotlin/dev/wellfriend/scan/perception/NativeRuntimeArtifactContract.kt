package dev.wellfriend.scan.perception

/**
 * Build/sync validation is authoritative for binary bytes. This bounded parser makes the
 * packaged manifest contract visible to host tests without adding an unreviewed JSON dependency.
 */
object NativeRuntimeArtifactContract {
    private val requiredLibraries = setOf(
        "arm64-v8a/libwellfriend_perception.so",
        "arm64-v8a/libwellfriend_perception_jni.so",
        "x86_64/libwellfriend_perception.so",
        "x86_64/libwellfriend_perception_jni.so",
    )

    fun validate(manifestJson: String, checksumsJson: String): Result<String> = runCatching {
        require(Regex("\\\"schema_version\\\"\\s*:\\s*1").containsMatchIn(manifestJson)) { "unsupported artifact manifest schema" }
        require(Regex("\\\"artifact_kind\\\"\\s*:\\s*\\\"wellfriend-android-abi\\\"").containsMatchIn(manifestJson)) { "unexpected artifact kind" }
        val sha = Regex("\\\"source_sha\\\"\\s*:\\s*\\\"([0-9a-f]{40})\\\"").find(manifestJson)?.groupValues?.get(1)
            ?: error("missing source SHA")
        requiredLibraries.forEach { library ->
            require(manifestJson.contains("\"$library\"")) { "required library missing: $library" }
            require(checksumsJson.contains("\"$library\"")) { "checksum missing: $library" }
        }
        require(Regex("[0-9a-f]{64}").containsMatchIn(checksumsJson)) { "checksum digest missing" }
        sha
    }
}

/** Process-local, non-sensitive artifact metadata displayed by the product debug panel. */
data class NativeRuntimeArtifactMetadata(val sourceSha: String?, val schemaVersion: Int?, val warning: String?)

object NativeRuntimeArtifactDiagnostics {
    @Volatile private var metadata = NativeRuntimeArtifactMetadata(null, null, "native runtime artifact manifest has not been loaded")

    fun configure(manifestJson: String, checksumsJson: String) {
        metadata = NativeRuntimeArtifactContract.validate(manifestJson, checksumsJson)
            .fold(
                onSuccess = { NativeRuntimeArtifactMetadata(it, 1, null) },
                onFailure = { NativeRuntimeArtifactMetadata(null, null, it.message ?: "native runtime artifact validation failed") },
            )
    }

    fun snapshot(): NativeRuntimeArtifactMetadata = metadata
}
