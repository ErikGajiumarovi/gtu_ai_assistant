package com.gtu.aiassistant.domain.model

abstract class AggregateRoot<ID : Any>(
    open val id: ID,
    open val version: Long
) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AggregateRoot<*>

        return id == other.id
    }

    final override fun hashCode(): Int = id.hashCode()
}
