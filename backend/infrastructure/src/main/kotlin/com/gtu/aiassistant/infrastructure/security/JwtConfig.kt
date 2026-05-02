package com.gtu.aiassistant.infrastructure.security

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val ttlSeconds: Long
)
