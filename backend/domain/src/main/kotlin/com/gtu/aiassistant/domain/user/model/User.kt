package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.gtu.aiassistant.domain.model.AggregateRoot
import com.gtu.aiassistant.domain.model.DomainError

class User private constructor(
    override val id: UserId,
    override val version: Long,
    val name: UserName,
    val lastName: UserLastName,
    val email: UserEmail,
    val passwordHash: UserPasswordHash
) : AggregateRoot<UserId>(id, version) {
    companion object {
        fun create(
            id: UserId,
            version: Long,
            name: UserName,
            lastName: UserLastName,
            email: UserEmail,
            passwordHash: UserPasswordHash
        ): Either<DomainError, User> =
            either {
                ensure(version >= 0L) { UserError.InvalidVersion }

                User(
                    id = id,
                    version = version,
                    name = name,
                    lastName = lastName,
                    email = email,
                    passwordHash = passwordHash
                )
            }

        fun fromTrusted(
            id: UserId,
            version: Long,
            name: UserName,
            lastName: UserLastName,
            email: UserEmail,
            passwordHash: UserPasswordHash
        ): User =
            User(
                id = id,
                version = version,
                name = name,
                lastName = lastName,
                email = email,
                passwordHash = passwordHash
            )
    }
}

sealed interface UserError : DomainError {
    data object InvalidVersion : UserError
}
