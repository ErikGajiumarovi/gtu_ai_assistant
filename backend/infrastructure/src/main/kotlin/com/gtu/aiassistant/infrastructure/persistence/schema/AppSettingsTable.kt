package com.gtu.aiassistant.infrastructure.persistence.schema

import org.jetbrains.exposed.v1.core.Table

object AppSettingsTable : Table(name = "app_settings") {
    val key = varchar("key", length = 120)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}
