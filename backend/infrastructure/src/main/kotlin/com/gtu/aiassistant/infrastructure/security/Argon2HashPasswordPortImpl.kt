package com.gtu.aiassistant.infrastructure.security

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserPassword
import com.gtu.aiassistant.domain.user.model.UserPasswordHash
import com.gtu.aiassistant.domain.user.port.output.HashPasswordPort
import de.mkammerer.argon2.Argon2Factory

class Argon2HashPasswordPortImpl : HashPasswordPort {
    override suspend fun invoke(password: UserPassword): Either<InfrastructureError, UserPasswordHash> =
        Either.catch {
            val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
            val secret = password.value.toCharArray()

            try {
                UserPasswordHash.fromTrusted(
                    argon2.hash(3, 65_536, 1, secret)
                )
            } finally {
                argon2.wipeArray(secret)
            }
        }.mapLeft(::InfrastructureError)
}
