package com.gtu.aiassistant.application.user

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPassword
import com.gtu.aiassistant.domain.user.model.UserPasswordHash
import com.gtu.aiassistant.domain.user.port.input.RegisterUserCommand
import com.gtu.aiassistant.domain.user.port.input.RegisterUserError
import com.gtu.aiassistant.domain.user.port.output.ExistsUserPort
import com.gtu.aiassistant.domain.user.port.output.HashPasswordPort
import com.gtu.aiassistant.domain.user.port.output.SaveUserPort
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterUserUseCaseImplTest {
    @Test
    fun `registration generates a uuid internally and saves a user with hashed password`() = runBlocking {
        var savedUser: User? = null

        val useCase = RegisterUserUseCaseImpl(
            existsUserPort = ExistsUserPort { Either.Right(false) },
            hashPasswordPort = HashPasswordPort { Either.Right(UserPasswordHash.fromTrusted("hashed-secret")) },
            saveUserPort = SaveUserPort { user ->
                savedUser = user
                Either.Right(user)
            }
        )

        val result = useCase(validRegisterCommand())

        assertTrue(result.isRight())

        val user = result.getOrNull()!!.user
        assertNotNull(savedUser)
        assertEquals(user.id, savedUser.id)
        assertEquals("hashed-secret", user.passwordHash.value)
        assertTrue(user.id.value.toString().isNotBlank())
        assertEquals(0L, user.version)
    }

    @Test
    fun `registration rejects duplicate email before hashing or saving`() = runBlocking {
        var hashed = false
        var saved = false

        val useCase = RegisterUserUseCaseImpl(
            existsUserPort = ExistsUserPort { strategy ->
                val byEmail = strategy as ExistsUserPort.Strategy.ByEmail
                Either.Right(byEmail.email.value == "agent@example.com")
            },
            hashPasswordPort = HashPasswordPort {
                hashed = true
                Either.Right(UserPasswordHash.fromTrusted("hashed-secret"))
            },
            saveUserPort = SaveUserPort { user ->
                saved = true
                Either.Right(user)
            }
        )

        val result = useCase(validRegisterCommand())

        assertIs<RegisterUserError.EmailAlreadyTaken>(result.leftOrNull())
        assertEquals(false, hashed)
        assertEquals(false, saved)
    }

    @Test
    fun `registration maps duplicate check hashing and persistence failures`() = runBlocking {
        val duplicateCheckFailure = RegisterUserUseCaseImpl(
            existsUserPort = ExistsUserPort { Either.Left(infrastructureError("duplicate-check")) },
            hashPasswordPort = HashPasswordPort { Either.Right(UserPasswordHash.fromTrusted("hashed-secret")) },
            saveUserPort = SaveUserPort { Either.Right(it) }
        )(validRegisterCommand()).leftOrNull()

        val hashingFailure = RegisterUserUseCaseImpl(
            existsUserPort = ExistsUserPort { Either.Right(false) },
            hashPasswordPort = HashPasswordPort { Either.Left(infrastructureError("hash")) },
            saveUserPort = SaveUserPort { Either.Right(it) }
        )(validRegisterCommand()).leftOrNull()

        val persistenceFailure = RegisterUserUseCaseImpl(
            existsUserPort = ExistsUserPort { Either.Right(false) },
            hashPasswordPort = HashPasswordPort { Either.Right(UserPasswordHash.fromTrusted("hashed-secret")) },
            saveUserPort = SaveUserPort { Either.Left(infrastructureError("save")) }
        )(validRegisterCommand()).leftOrNull()

        assertIs<RegisterUserError.DuplicateCheckFailed>(duplicateCheckFailure)
        assertIs<RegisterUserError.PasswordHashingFailed>(hashingFailure)
        assertIs<RegisterUserError.PersistenceFailed>(persistenceFailure)
    }
}

private fun validRegisterCommand() =
    RegisterUserCommand(
        name = UserName.create("Agent").getOrNull()!!,
        lastName = UserLastName.create("User").getOrNull()!!,
        email = UserEmail.create("agent@example.com").getOrNull()!!,
        password = UserPassword.create("super-secret-password").getOrNull()!!
    )

private fun infrastructureError(label: String) =
    InfrastructureError(IllegalStateException(label))
