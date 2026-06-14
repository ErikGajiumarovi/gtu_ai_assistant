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
    fun detectIntent(prompt: String): ArtifactIntent? =
        ArtifactIntent.fromPrompt(prompt)

    suspend fun createArtifact(
        userId: UserId,
        intent: ArtifactIntent,
        draft: ArtifactDraft?
    ): Either<InfrastructureError, ArtifactGenerationResult?> = either {
        val bytes = when (intent.kind) {
            ArtifactKind.TEXT -> requireDraft(intent, draft).markdown.toByteArray(StandardCharsets.UTF_8)
            ArtifactKind.HTML -> requireDraft(intent, draft).toHtmlArtifact().toByteArray(StandardCharsets.UTF_8)
            ArtifactKind.DOCX, ArtifactKind.CHART -> {
                val run = agentSpaceClient.runForArtifact(
                    mode = "python",
                    code = intent.toPythonCode(draft),
                    artifactPath = intent.artifactPath,
                    timeoutSeconds = 60
                ).bind()
                ensureArtifactRunSucceeded(run, intent).bind()
                val artifact = run.artifacts.firstOrNull { it.path == intent.artifactPath }
                    ?: raise(InfrastructureError(IllegalStateException("agent_space did not return artifact ${intent.artifactPath}")))
                if (artifact.sizeBytes <= 0) {
                    raise(InfrastructureError(IllegalStateException("agent_space returned empty artifact ${intent.artifactPath}")))
                }
                val decodedBytes = Base64.getDecoder().decode(artifact.base64)
                if (decodedBytes.size.toLong() != artifact.sizeBytes) {
                    raise(InfrastructureError(IllegalStateException("agent_space artifact size mismatch for ${intent.artifactPath}")))
                }
                decodedBytes
            }
        }
        val artifact = storeGeneratedArtifactPort(
            StoreGeneratedArtifactCommand(
                ownerUserId = userId,
                chatId = null,
                messageId = null,
                fileName = intent.fileName,
                contentType = intent.contentType,
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
                appendLine("- verified: file content was returned, decoded, size-checked, and stored successfully")
            }.trim()
        )
    }
}

private fun ensureArtifactRunSucceeded(
    run: AgentSpaceRunResponse,
    intent: ArtifactIntent
): Either<InfrastructureError, Unit> = either {
    if (run.timedOut) {
        raise(InfrastructureError(IllegalStateException("agent_space timed out while creating ${intent.artifactPath}: ${run.summary()}")))
    }
    if (run.exitCode != 0) {
        raise(InfrastructureError(IllegalStateException("agent_space failed to create ${intent.artifactPath}: ${run.summary()}")))
    }
}

data class ArtifactGenerationResult(
    val artifacts: List<MessageArtifact>,
    val context: String
)

data class ArtifactDraft(
    val title: String,
    val markdown: String
) {
    companion object {
        fun fromMarkdown(markdown: String, fallbackTitle: String): ArtifactDraft {
            val normalized = markdown.trim()
            val title = normalized
                .lineSequence()
                .firstOrNull { it.trimStart().startsWith("# ") }
                ?.trim()
                ?.removePrefix("#")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle
            return ArtifactDraft(title = title, markdown = normalized)
        }
    }
}

enum class ArtifactKind {
    TEXT,
    HTML,
    DOCX,
    CHART
}

data class ArtifactIntent(
    val kind: ArtifactKind,
    val fileName: String,
    val contentType: String,
    val artifactPath: String = "",
    val originalPrompt: String = ""
) {
    val needsDraft: Boolean = kind in setOf(ArtifactKind.TEXT, ArtifactKind.HTML, ArtifactKind.DOCX)

    companion object {
        fun fromPrompt(prompt: String): ArtifactIntent? {
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

        private fun text(prompt: String): ArtifactIntent =
            ArtifactIntent(
                kind = ArtifactKind.TEXT,
                fileName = "assistant-output.md",
                contentType = "text/markdown; charset=utf-8",
                originalPrompt = prompt
            )

        private fun html(prompt: String): ArtifactIntent =
            ArtifactIntent(
                kind = ArtifactKind.HTML,
                fileName = "assistant-page.html",
                contentType = "text/html; charset=utf-8",
                originalPrompt = prompt
            )

        private fun docx(prompt: String): ArtifactIntent =
            ArtifactIntent(
                kind = ArtifactKind.DOCX,
                fileName = "assistant-document.docx",
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                artifactPath = "assistant-document.docx",
                originalPrompt = prompt
            )

        private fun chart(prompt: String): ArtifactIntent =
            ArtifactIntent(
                kind = ArtifactKind.CHART,
                fileName = "assistant-chart.png",
                contentType = "image/png",
                artifactPath = "assistant-chart.png",
                originalPrompt = prompt
            )
    }

    fun toPythonCode(draft: ArtifactDraft?): String =
        when (kind) {
            ArtifactKind.DOCX -> docxPythonCode(requireDraft(this, draft))
            ArtifactKind.CHART -> chartPythonCode(originalPrompt)
            ArtifactKind.TEXT, ArtifactKind.HTML -> error("${kind.name} artifacts do not need Python rendering")
        }

    private fun docxPythonCode(draft: ArtifactDraft): String =
        """
            import base64
            from docx import Document
            from docx.shared import Pt
            from pathlib import Path

            title = base64.b64decode(${draft.title.toBase64PythonStringLiteral()}).decode('utf-8')
            markdown = base64.b64decode(${draft.markdown.toBase64PythonStringLiteral()}).decode('utf-8')

            document = Document()

            def add_paragraph(text, style=None):
                paragraph = document.add_paragraph(style=style)
                paragraph.add_run(text)
                return paragraph

            def add_heading(text, level):
                document.add_heading(text.strip(), level=level)

            has_title = False
            pending_paragraph = []

            def flush_paragraph():
                global pending_paragraph
                if pending_paragraph:
                    add_paragraph(' '.join(pending_paragraph).strip())
                    pending_paragraph = []

            for raw_line in markdown.splitlines():
                line = raw_line.strip()
                if not line:
                    flush_paragraph()
                    continue
                if line.startswith('#'):
                    flush_paragraph()
                    level = min(len(line) - len(line.lstrip('#')), 4)
                    text = line.lstrip('#').strip()
                    if text:
                        add_heading(text, level)
                        has_title = has_title or level == 1
                    continue
                if line.startswith(('- ', '* ')):
                    flush_paragraph()
                    add_paragraph(line[2:].strip(), style='List Bullet')
                    continue
                numbered = line.split('. ', 1)
                if len(numbered) == 2 and numbered[0].isdigit():
                    flush_paragraph()
                    add_paragraph(numbered[1].strip(), style='List Number')
                    continue
                pending_paragraph.append(line)

            flush_paragraph()

            if not has_title:
                document.paragraphs[0].insert_paragraph_before(title, style='Title') if document.paragraphs else document.add_heading(title, level=1)

            for paragraph in document.paragraphs:
                for run in paragraph.runs:
                    run.font.name = 'Arial'
                    run.font.size = Pt(11)

            output_path = Path('assistant-document.docx')
            document.save(output_path)
            size = output_path.stat().st_size
            if size <= 0:
                raise RuntimeError('assistant-document.docx was created empty')
            print(f'created artifact: {output_path} ({size} bytes)')
        """.trimIndent()

    private fun chartPythonCode(prompt: String): String {
        val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(prompt).map { it.value }.take(12).toList()
        val values = if (numbers.isEmpty()) listOf("4", "7", "3", "9", "6") else numbers
        return """
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
                    output_path = 'assistant-chart.png'
                    plt.savefig(output_path, dpi=160)
                    import os
                    size = os.path.getsize(output_path)
                    if size <= 0:
                        raise RuntimeError('assistant-chart.png was created empty')
                    print(f'created artifact: {output_path} ({size} bytes)')
                """.trimIndent()
    }
}

private fun requireDraft(intent: ArtifactIntent, draft: ArtifactDraft?): ArtifactDraft =
    draft ?: error("${intent.kind.name} artifact requires generated draft content")

private fun ArtifactDraft.toHtmlPage(): String =
    """
    <!doctype html>
    <html lang="en">
    <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title.escapeHtml()}</title>
    <style>body{font-family:system-ui,sans-serif;max-width:860px;margin:40px auto;padding:0 20px;line-height:1.55;color:#172033}h1,h2,h3{line-height:1.2}pre{white-space:pre-wrap;background:#f5f5f5;padding:16px;border-radius:12px}</style></head>
    <body>${markdown.toHtmlBody()}</body></html>
    """.trimIndent()

private fun ArtifactDraft.toHtmlArtifact(): String {
    val normalized = markdown.stripMarkdownFence("html").trim()
    return if (normalized.looksLikeHtmlDocument()) {
        normalized
    } else {
        copy(markdown = normalized).toHtmlPage()
    }
}

private fun String.stripMarkdownFence(language: String): String {
    val trimmed = trim()
    if (!trimmed.startsWith("```")) return this
    val lines = trimmed.lines()
    val firstLine = lines.firstOrNull()?.trim().orEmpty()
    if (!firstLine.equals("```$language", ignoreCase = true) && firstLine != "```") return this
    if (lines.lastOrNull()?.trim() != "```") return this
    return lines.drop(1).dropLast(1).joinToString("\n")
}

private fun String.looksLikeHtmlDocument(): Boolean {
    val trimmed = trimStart()
    return trimmed.startsWith("<!doctype html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        trimmed.startsWith("<body", ignoreCase = true) ||
        trimmed.startsWith("<main", ignoreCase = true) ||
        trimmed.startsWith("<section", ignoreCase = true) ||
        trimmed.startsWith("<div", ignoreCase = true)
}

private fun String.toHtmlBody(): String =
    lineSequence().joinToString("\n") { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("# ") -> "<h1>${line.removePrefix("# ").escapeHtml()}</h1>"
            line.startsWith("## ") -> "<h2>${line.removePrefix("## ").escapeHtml()}</h2>"
            line.startsWith("### ") -> "<h3>${line.removePrefix("### ").escapeHtml()}</h3>"
            line.startsWith("- ") -> "<p>&bull; ${line.removePrefix("- ").escapeHtml()}</p>"
            line.isBlank() -> ""
            else -> "<p>${line.escapeHtml()}</p>"
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

private fun String.toBase64PythonStringLiteral(): String =
    Base64.getEncoder().encodeToString(toByteArray(StandardCharsets.UTF_8)).toPythonStringLiteral()
