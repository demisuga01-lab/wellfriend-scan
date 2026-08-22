package dev.wellfriend.scan.perception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeRuntimeArtifactContractTest {
    @Test fun `expected ABI manifest validates`() {
        val libraries = listOf(
            "arm64-v8a/libwellfriend_perception.so", "arm64-v8a/libwellfriend_perception_jni.so",
            "x86_64/libwellfriend_perception.so", "x86_64/libwellfriend_perception_jni.so",
        )
        val manifest = "{\"schema_version\":1,\"artifact_kind\":\"wellfriend-android-abi\",\"source_sha\":\"${"a".repeat(40)}\",\"page_size_alignment_bytes\":16384,\"libraries\":[${libraries.joinToString { "{\"file\":\"$it\"}" }}]}"
        val checksums = "{\"schema_version\":1,\"files\":[${libraries.joinToString { "{\"path\":\"$it\",\"sha256\":\"${"b".repeat(64)}\"}" }}]}"
        assertEquals("a".repeat(40), NativeRuntimeArtifactContract.validate(manifest, checksums).getOrThrow())
    }

    @Test fun `missing required ABI is rejected`() {
        assertTrue(NativeRuntimeArtifactContract.validate("{\"schema_version\":1,\"artifact_kind\":\"wellfriend-android-abi\",\"source_sha\":\"${"a".repeat(40)}\"}", "{}").isFailure)
    }

    @Test fun `missing 16 KiB page-size declaration is rejected`() {
        val manifest = "{\"schema_version\":1,\"artifact_kind\":\"wellfriend-android-abi\",\"source_sha\":\"${"a".repeat(40)}\",\"libraries\":[]}"
        assertTrue(NativeRuntimeArtifactContract.validate(manifest, "{}").isFailure)
    }
}
