package com.gtu.aiassistant.domain.user.port.input

import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.domain.model.InfrastructureError

sealed interface CreateUserError {
    data class InvalidDomainState(
        val reason: DomainError
    ) : CreateUserError

    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : CreateUserError
}
