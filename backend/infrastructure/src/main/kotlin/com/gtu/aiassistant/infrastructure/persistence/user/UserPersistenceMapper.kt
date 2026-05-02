package com.gtu.aiassistant.infrastructure.persistence.user

import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPasswordHash
import org.jetbrains.exposed.v1.core.ResultRow
import java.util.UUID

internal fun ResultRow.toDomainUser(): User =
    User.fromTrusted(
        id = UserId.fromTrusted(UUID.fromString(this[UserRecords.id])),
        version = this[UserRecords.version],
        name = UserName.fromTrusted(this[UserRecords.name]),
        lastName = UserLastName.fromTrusted(this[UserRecords.lastName]),
        email = UserEmail.fromTrusted(this[UserRecords.email]),
        passwordHash = UserPasswordHash.fromTrusted(this[UserRecords.passwordHash])
    )
