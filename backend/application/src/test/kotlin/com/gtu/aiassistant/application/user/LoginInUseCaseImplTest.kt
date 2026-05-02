package com.gtu.aiassistant.application.user

import arrow.core.Either
import com.gtu.aiassistant.domain.model.InfrastructureError
import com.gtu.aiassistant.domain.user.model.User
import com.gtu.aiassistant.domain.user.model.UserEmail
import com.gtu.aiassistant.domain.user.model.UserId
import com.gtu.aiassistant.domain.user.model.UserLastName
import com.gtu.aiassistant.domain.user.model.UserName
import com.gtu.aiassistant.domain.user.model.UserPassword
import com.gtu.aiassistant.domain.user.model.UserPasswordHash
import com.gtu.aiassistant.domain.user.port.input.LoginInCommand
import com.gtu.aiassistant.domain.user.port.input.LoginInError
import com.gtu.aiassistant.domain.user.port.output.FindUserPort
import com.gtu.aiassistant.domain.user.port.output.IssueJwtPort
import com.gtu.aiassistant.domain.user.port.output.VerifyPasswordPort
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoginInUseCaseImplTest {
    @Test
    fun `login returns jwt for valid email and password`() = runBlocking {
        val user = sampleUser()
        val useCase = LoginInUseCaseImpl(
            findUserPort = FindUserPort { Either.Right(user) },
            verifyPasswordPort = VerifyPasswordPort { _, _ -> Either.Right(true) },
            issueJwtPort = IssueJwtPort { Either.Right("jwt-token") }
        )

        val result = useCase(validLoginCommand())

        assertTrue(result.isRight())
        assertEquals("jwt-token", result.getOrNull()!!.jwt)
    }

    @Test
    fun `login returns invalid credentials for unknown email and wrong password`() = runBlocking {
        val unknownEmailResult = LoginInUseCaseImpl(
            findUserPort = FindUserPort { Either.Right(null) },
            verifyPasswordPort = VerifyPasswordPort { _, _ -> Either.Right(true) },
            issueJwtPort = IssueJwtPort { Either.Right("jwt-token") }
        )(validLoginCommand()).leftOrNull()

        val wrongPasswordResult = LoginInUseCaseImpl(
            findUserPort = FindUserPort { Either.Right(sampleUser()) },
            verifyPasswordPort = VerifyPasswordPort { _, _ -> Either.Right(false) },
            issueJwtPort = IssueJwtPort { Either.Right("jwt-token") }
        )(validLoginCommand()).leftOrNull()

        assertIs<LoginInError.InvalidCredentials>(unknownEmailResult)
        assertIs<LoginInError.InvalidCredentials>(wrongPasswordResult)
    }

    @Test
    fun `login maps find verify and jwt issuing failures`() = runBlocking {
        val findFailure = LoginInUseCaseImpl(
            findUserPort = FindUserPort { Either.Left(infrastructureError("find")) },
            verifyPasswordPort = VerifyPasswordPort { _, _ -> Either.Right(true) },
            issueJwtPort = IssueJwtPort { Either.Right("jwt-token") }
        )(validLoginCommand()).leftOrNull()

        val verifyFailure = LoginInUseCaseImpl(
            findUserPort = FindUserPort { Either.Right(sampleUser()) },
            verifyPasswordPort = VerifyPasswordPort { _, _ -> Either.Left(infrastructureError("verify")) },
            issueJwtPort = IssueJwtPort { Either.Right("jwt-token") }
        )(validLoginCommand()).leftOrNull()

        val jwtFailure = LoginInUseCaseImpl(
            findUserPort = FindUserPort { Either.Right(sampleUser()) },
            verifyPasswordPort = VerifyPasswordPort { _, _ -> Either.Right(true) },
            issueJwtPort = IssueJwtPort { Either.Left(infrastructureError("jwt")) }
        )(validLoginCommand()).leftOrNull()

        assertIs<LoginInError.FindFailed>(findFailure)
        assertIs<LoginInError.PasswordVerificationFailed>(verifyFailure)
        assertIs<LoginInError.JwtIssuingFailed>(jwtFailure)
    }
}

private fun validLoginCommand() =
    LoginInCommand(
        email = UserEmail.create("agent@example.com").getOrNull()!!,
        password = UserPassword.create("super-secret-password").getOrNull()!!
    )

private fun sampleUser(): User =
    User.fromTrusted(
        id = UserId.fromTrusted(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        version = 0L,
        name = UserName.fromTrusted("Agent"),
        lastName = UserLastName.fromTrusted("User"),
        email = UserEmail.fromTrusted("agent@example.com"),
        passwordHash = UserPasswordHash.fromTrusted("hashed-secret")
    )

private fun infrastructureError(label: String) =
    InfrastructureError(IllegalStateException(label))
