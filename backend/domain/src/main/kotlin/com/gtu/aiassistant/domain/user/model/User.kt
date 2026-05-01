package com.gtu.aiassistant.domain.user.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.gtu.aiassistant.domain.model.AggregateRoot
import com.gtu.aiassistant.domain.model.DomainError

class User private constructor(
    override val id: UserId,
    override val version: Long,
    val name: UserName,
    val lastName: UserLastName,
    val email: UserEmail
) : AggregateRoot<UserId>(id, version) {
    companion object {
        fun create(
            id: UserId,
            version: Long,
            name: UserName,
            lastName: UserLastName,
            email: UserEmail
        ): Either<DomainError, User> =
            when {
                version < 0L -> UserError.InvalidVersion.left()
                else -> User(
                    id = id,
                    version = version,
                    name = name,
                    lastName = lastName,
                    email = email
                ).right()
            }

        fun fromTrusted(
            id: UserId,
            version: Long,
            name: UserName,
            lastName: UserLastName,
            email: UserEmail
        ): User =
            User(
                id = id,
                version = version,
                name = name,
                lastName = lastName,
                email = email
            )
    }
}

sealed interface UserError : DomainError {
    data object InvalidVersion : UserError
}
