package com.gtu.aiassistant.infrastructure.security

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.UserPassword
import com.gtu.aiassistant.domain.user.model.UserPasswordHash
import com.gtu.aiassistant.domain.user.port.output.VerifyPasswordPort
import de.mkammerer.argon2.Argon2Factory

class Argon2VerifyPasswordPortImpl : VerifyPasswordPort {
    override suspend fun invoke(
        password: UserPassword,
        passwordHash: UserPasswordHash
    ): Either<InfrastructureError, Boolean> =
        Either.catch {
            val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
            val secret = password.value.toCharArray()

            try {
                argon2.verify(passwordHash.value, secret)
            } finally {
                argon2.wipeArray(secret)
            }
        }.mapLeft(::InfrastructureError)
}
