package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import java.util.Locale

class PgVectorColumnType : ColumnType<List<Float>>() {
    override fun sqlType(): String = "vector"

    override fun valueFromDB(value: Any): List<Float> =
        parseVector(value.toString())

    override fun notNullValueToDB(value: List<Float>): Any =
        value.toVectorLiteral()

    override fun nonNullValueToString(value: List<Float>): String =
        "'${value.toVectorLiteral()}'::vector"

    override fun parameterMarker(value: List<Float>?): String = "?::vector"
}

fun Table.vector(name: String): Column<List<Float>> =
    registerColumn(name, PgVectorColumnType())

private fun List<Float>.toVectorLiteral(): String =
    joinToString(prefix = "[", postfix = "]") { value ->
        val safeValue = if (value.isFinite()) value else 0.0f
        String.format(Locale.US, "%.8f", safeValue)
    }

private fun parseVector(raw: String): List<Float> =
    raw
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .mapNotNull { item ->
            item.trim().takeIf { it.isNotBlank() }?.toFloatOrNull()
        }
