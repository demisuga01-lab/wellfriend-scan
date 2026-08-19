package dev.wellfriend.scan.export

import dev.wellfriend.scan.core.ScanSession
import java.io.File

enum class ExportFormat { JPEG, PNG, PDF_PLACEHOLDER, JSON_DEBUG }
enum class ExportProgress { QUEUED, WRITING, COMPLETE, FAILED }

data class ExportRequest(
    val session: ScanSession,
    val format: ExportFormat,
    val outputDirectory: File,
)

data class ExportResult(
    val format: ExportFormat,
    val progress: ExportProgress,
    val outputFiles: List<File>,
    val diagnostics: List<String>,
)

sealed class ExportError(message: String) : IllegalArgumentException(message) {
    data object EmptySession : ExportError("at least one accepted scan page is required")
    data object MissingOutputDirectory : ExportError("export output directory is not writable")
    data object PdfNotImplemented : ExportError("PDF export is a declared MP7 placeholder")
    data class Unsupported(val format: ExportFormat) : ExportError("$format export is not implemented in MP7")
}

object ScanExporter {
    fun validate(request: ExportRequest): Result<Unit> = runCatching {
        if (request.session.pages.isEmpty()) throw ExportError.EmptySession
        if (!request.outputDirectory.exists() && !request.outputDirectory.mkdirs()) throw ExportError.MissingOutputDirectory
        if (!request.outputDirectory.isDirectory || !request.outputDirectory.canWrite()) throw ExportError.MissingOutputDirectory
    }

    /** Debug export deliberately omits image bytes and records only session metadata and URI references. */
    fun exportDebugJson(request: ExportRequest): ExportResult {
        validate(request).getOrThrow()
        require(request.format == ExportFormat.JSON_DEBUG) { "debug exporter only writes JSON_DEBUG" }
        val file = File(request.outputDirectory, "wellfriend-scan-${request.session.id}.json")
        file.writeText(sessionJson(request.session))
        return ExportResult(
            format = ExportFormat.JSON_DEBUG,
            progress = ExportProgress.COMPLETE,
            outputFiles = listOf(file),
            diagnostics = listOf("debug JSON only; image content and OCR are not exported"),
        )
    }

    fun unavailable(request: ExportRequest): Nothing = when (request.format) {
        ExportFormat.PDF_PLACEHOLDER -> throw ExportError.PdfNotImplemented
        ExportFormat.JPEG, ExportFormat.PNG -> throw ExportError.Unsupported(request.format)
        ExportFormat.JSON_DEBUG -> error("use exportDebugJson for JSON_DEBUG")
    }

    private fun sessionJson(session: ScanSession): String = buildString {
        appendLine("{")
        appendLine("  \"schema_version\": 1,")
        appendLine("  \"session_id\": \"${escape(session.id)}\",")
        appendLine("  \"domain\": \"${escape(session.domain)}\",")
        appendLine("  \"pages\": [")
        session.pages.forEachIndexed { index, page ->
            append("    {\"id\":\"${escape(page.id)}\",\"source_uri\":\"${escape(page.sourceUri)}\",")
            append("\"filter\":\"${page.filter}\",\"rotation_degrees\":${page.rotationDegrees},")
            append("\"geometry_source\":\"")
            append(page.effectiveGeometry?.source ?: "none")
            append("\"}")
            appendLine(if (index == session.pages.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
