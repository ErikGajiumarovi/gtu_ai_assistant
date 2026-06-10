package com.gtu.aiassistant.infrastructure.ocr

import arrow.core.Either
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrCommand
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrPort
import com.gtu.aiassistant.domain.materials.port.output.MaterialOcrResult
import com.gtu.aiassistant.domain.model.InfrastructureError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

class TesseractMaterialOcrPortImpl(
    private val config: TesseractOcrConfig
) : MaterialOcrPort {
    override suspend fun invoke(command: MaterialOcrCommand): Either<InfrastructureError, MaterialOcrResult> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val imagePath = Files.createTempFile("material-ocr-page-${command.pageNumber}-", ".png")
                try {
                    imagePath.writeBytes(command.imageBytes)
                    val process = ProcessBuilder(
                        config.command,
                        imagePath.toString(),
                        "stdout",
                        "-l",
                        config.languages,
                        "tsv"
                    )
                        .redirectErrorStream(false)
                        .start()

                    val completed = process.waitFor(config.timeout.toMillis(), TimeUnit.MILLISECONDS)
                    if (!completed) {
                        process.destroyForcibly()
                        throw IllegalStateException("Tesseract OCR timed out after ${config.timeout.seconds}s")
                    }

                    val stdout = process.inputStream.bufferedReader().use { it.readText() }
                    val stderr = process.errorStream.bufferedReader().use { it.readText() }
                    if (process.exitValue() != 0) {
                        throw IllegalStateException(stderr.ifBlank { "Tesseract OCR failed with exit code ${process.exitValue()}" })
                    }

                    parseTsv(stdout)
                } finally {
                    imagePath.deleteIfExists()
                }
            }.mapLeft(::InfrastructureError)
        }

    private fun parseTsv(tsv: String): MaterialOcrResult {
        val words = mutableListOf<String>()
        val confidences = mutableListOf<Double>()
        tsv.lineSequence()
            .drop(1)
            .forEach { line ->
                val columns = line.split('\t')
                if (columns.size < 12 || columns[0] != "5") return@forEach
                val word = columns[11].trim()
                if (word.isBlank()) return@forEach
                words += word
                columns[10].toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { confidences += it }
            }

        return MaterialOcrResult(
            text = words.joinToString(" "),
            averageConfidence = confidences.takeIf { it.isNotEmpty() }?.average()
        )
    }
}

data class TesseractOcrConfig(
    val command: String = DEFAULT_COMMAND,
    val languages: String = DEFAULT_LANGUAGES,
    val timeout: Duration = DEFAULT_TIMEOUT
) {
    init {
        require(command.isNotBlank()) { "Tesseract command must not be blank" }
        require(languages.isNotBlank()) { "Tesseract languages must not be blank" }
        require(!timeout.isNegative && !timeout.isZero) { "Tesseract timeout must be positive" }
    }

    companion object {
        const val DEFAULT_COMMAND = "tesseract"
        const val DEFAULT_LANGUAGES = "eng+rus"
        val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(60)
    }
}
