package com.gtu.aiassistant.infrastructure.ai

import arrow.core.Either
import arrow.core.raise.either
import com.gtu.aiassistant.domain.artifacts.model.MessageArtifact
import com.gtu.aiassistant.domain.artifacts.model.StoreGeneratedArtifactCommand
import com.gtu.aiassistant.domain.artifacts.model.isViewableHtmlArtifact
import com.gtu.aiassistant.domain.artifacts.port.output.StoreGeneratedArtifactPort
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserId
import java.nio.charset.StandardCharsets
import java.util.Base64

class AgentArtifactService(
    private val agentSpaceClient: AgentSpaceClient,
    private val storeGeneratedArtifactPort: StoreGeneratedArtifactPort
) {
    suspend fun maybeCreateArtifact(
        userId: UserId,
        prompt: String
    ): Either<InfrastructureError, ArtifactGenerationResult?> = either {
        val request = ArtifactRequest.fromPrompt(prompt) ?: return@either null
        val bytes = when (request.kind) {
            ArtifactKind.TEXT -> request.textContent.toByteArray(StandardCharsets.UTF_8)
            ArtifactKind.HTML -> request.htmlContent.toByteArray(StandardCharsets.UTF_8)
            ArtifactKind.DOCX, ArtifactKind.CHART -> {
                val run = agentSpaceClient.runForArtifact(
                    mode = "python",
                    code = request.pythonCode,
                    artifactPath = request.artifactPath,
                    timeoutSeconds = 60
                ).bind()
                val artifact = run.artifacts.firstOrNull()
                    ?: raise(InfrastructureError(IllegalStateException("agent_space did not return artifact ${request.artifactPath}")))
                Base64.getDecoder().decode(artifact.base64)
            }
        }
        val artifact = storeGeneratedArtifactPort(
            StoreGeneratedArtifactCommand(
                ownerUserId = userId,
                chatId = null,
                messageId = null,
                fileName = request.fileName,
                contentType = request.contentType,
                bytes = bytes
            )
        ).bind()
        val downloadUrl = "/api/artifacts/${artifact.id.value}/download"
        val viewUrl = if (isViewableHtmlArtifact(artifact.fileName, artifact.contentType)) {
            "/api/artifacts/${artifact.id.value}/view"
        } else {
            null
        }
        val messageArtifact = MessageArtifact(
            id = artifact.id,
            fileName = artifact.fileName,
            contentType = artifact.contentType,
            sizeBytes = artifact.sizeBytes,
            downloadUrl = downloadUrl,
            viewUrl = viewUrl
        )
        ArtifactGenerationResult(
            artifacts = listOf(messageArtifact),
            context = buildString {
                appendLine("Generated artifact created:")
                appendLine("- ${messageArtifact.fileName} (${messageArtifact.contentType}, ${messageArtifact.sizeBytes} bytes)")
                appendLine("- download: ${messageArtifact.downloadUrl}")
                if (messageArtifact.viewUrl != null) appendLine("- open: ${messageArtifact.viewUrl}")
            }.trim()
        )
    }
}

data class ArtifactGenerationResult(
    val artifacts: List<MessageArtifact>,
    val context: String
)

private enum class ArtifactKind {
    TEXT,
    HTML,
    DOCX,
    CHART
}

private data class ArtifactRequest(
    val kind: ArtifactKind,
    val fileName: String,
    val contentType: String,
    val textContent: String = "",
    val htmlContent: String = "",
    val pythonCode: String = "",
    val artifactPath: String = ""
) {
    companion object {
        fun fromPrompt(prompt: String): ArtifactRequest? {
            val normalized = prompt.lowercase()
            val wantsArtifact = listOf(
                "создай", "сделай", "сгенерируй", "нарисуй", "построй", "сохрани", "экспорт", "выгрузи", "подготовь", "покажи",
                "create", "make", "generate", "draw", "plot", "build", "save", "export", "download", "prepare",
                ".docx", ".html", ".txt", ".md", ".csv", ".json", ".png"
            ).any { it in normalized }
            if (!wantsArtifact) return null
            return when {
                listOf("docx", "word", "ворд", "отчет", "отчёт", ".docx").any { it in normalized } -> docx(prompt)
                listOf("график", "диаграм", "chart", "plot", "visualization", "визуализа").any { it in normalized } -> chart(prompt)
                listOf("html", "страниц", "page", "interactive", "интерактив").any { it in normalized } -> html(prompt)
                listOf("создай файл", "сделай файл", "create file", "save file", ".txt", ".md", ".csv", ".json").any { it in normalized } -> text(prompt)
                else -> null
            }
        }

        private fun text(prompt: String): ArtifactRequest =
            ArtifactRequest(
                kind = ArtifactKind.TEXT,
                fileName = "assistant-output.md",
                contentType = "text/markdown; charset=utf-8",
                textContent = "# Assistant Output\n\n$prompt\n"
            )

        private fun html(prompt: String): ArtifactRequest =
            ArtifactRequest(
                kind = ArtifactKind.HTML,
                fileName = "assistant-page.html",
                contentType = "text/html; charset=utf-8",
                htmlContent = """
                    <!doctype html>
                    <html lang="en">
                    <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Assistant Page</title>
                    <style>body{font-family:system-ui,sans-serif;max-width:860px;margin:40px auto;padding:0 20px;line-height:1.5}pre{white-space:pre-wrap;background:#f5f5f5;padding:16px;border-radius:12px}</style></head>
                    <body><h1>Assistant Page</h1><pre>${prompt.escapeHtml()}</pre></body></html>
                """.trimIndent()
            )

        private fun docx(prompt: String): ArtifactRequest =
            ArtifactRequest(
                kind = ArtifactKind.DOCX,
                fileName = "assistant-document.docx",
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                artifactPath = "assistant-document.docx",
                pythonCode = """
                    from docx import Document
                    from docx.shared import Pt

                    document = Document()
                    document.add_heading('Assistant Document', level=1)
                    document.add_paragraph(${prompt.toPythonStringLiteral()})
                    document.add_paragraph('Generated by GTU AI Assistant.')
                    for paragraph in document.paragraphs:
                        for run in paragraph.runs:
                            run.font.name = 'Arial'
                            run.font.size = Pt(11)
                    document.save('assistant-document.docx')
                """.trimIndent()
            )

        private fun chart(prompt: String): ArtifactRequest {
            val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(prompt).map { it.value }.take(12).toList()
            val values = if (numbers.isEmpty()) listOf("4", "7", "3", "9", "6") else numbers
            return ArtifactRequest(
                kind = ArtifactKind.CHART,
                fileName = "assistant-chart.png",
                contentType = "image/png",
                artifactPath = "assistant-chart.png",
                pythonCode = """
                    import matplotlib
                    matplotlib.use('Agg')
                    import matplotlib.pyplot as plt

                    values = [${values.joinToString(", ")}]
                    labels = [f'Item {i+1}' for i in range(len(values))]
                    plt.figure(figsize=(9, 5))
                    plt.bar(labels, values, color='#2563eb')
                    plt.title('Generated Chart')
                    plt.ylabel('Value')
                    plt.xticks(rotation=30, ha='right')
                    plt.tight_layout()
                    plt.savefig('assistant-chart.png', dpi=160)
                """.trimIndent()
            )
        }
    }
}

private fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private fun String.toPythonStringLiteral(): String =
    "'''" + replace("\\", "\\\\").replace("'''", "'\"'\"'") + "'''"
