package com.gtu.aiassistant.infrastructure.security

import arrow.core.Either
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.port.output.IssueJwtPort
import java.time.Instant
import java.util.Date

class IssueJwtPortImpl(
    private val config: JwtConfig
) : IssueJwtPort {
    override suspend fun invoke(user: User): Either<InfrastructureError, String> =
        Either.catch {
            val now = Instant.now()

            JWT.create()
                .withIssuer(config.issuer)
                .withSubject(user.id.value.toString())
                .withClaim("email", user.email.value)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(config.ttlSeconds)))
                .sign(Algorithm.HMAC256(config.secret))
        }.mapLeft(::InfrastructureError)
}
