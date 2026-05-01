package com.gtu.aiassistant.infrastructure.persistence.support

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class JdbcPersistenceExecutor(
    private val database: Database
) {
    suspend fun <A> execute(block: () -> A): Either<InfrastructureError, A> =
        withContext(Dispatchers.IO) {
            try {
                Either.Right(
                    transaction(database) {
                        block()
                    }
                )
            } catch (cause: Throwable) {
                Either.Left(
                    InfrastructureError(cause = cause)
                )
            }
        }
}
