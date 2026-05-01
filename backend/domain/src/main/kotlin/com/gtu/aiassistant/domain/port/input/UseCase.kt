package com.gtu.aiassistant.domain.port.input

import arrow.core.Either

interface UseCase<I, E, O> {
    suspend operator fun invoke(input: I): Either<E, O>
}
