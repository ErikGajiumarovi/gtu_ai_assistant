package com.gtu.aiassistant.domain.user.port.input

import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName

data class CreateUserCommand(
    val id: UserId,
    val name: UserName,
    val lastName: UserLastName,
    val email: UserEmail
)
