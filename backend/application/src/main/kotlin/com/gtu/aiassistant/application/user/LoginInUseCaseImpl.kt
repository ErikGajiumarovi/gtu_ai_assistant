package com.gtu.aiassistant.application.user

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.gtu.aiassistant.domain.user.port.input.LoginInCommand
import com.gtu.aiassistant.domain.user.port.input.LoginInError
import com.gtu.aiassistant.domain.user.port.input.LoginInResult
import com.gtu.aiassistant.domain.user.port.input.LoginInUseCase
import com.gtu.aiassistant.domain.user.port.output.FindUserPort
import com.gtu.aiassistant.domain.user.port.output.IssueJwtPort
import com.gtu.aiassistant.domain.user.port.output.VerifyPasswordPort

class LoginInUseCaseImpl(
    private val findUserPort: FindUserPort,
    private val verifyPasswordPort: VerifyPasswordPort,
    private val issueJwtPort: IssueJwtPort
) : LoginInUseCase {
    override suspend fun invoke(command: LoginInCommand): Either<LoginInError, LoginInResult> =
        either {
            val user = findUserPort
                .invoke(FindUserPort.Strategy.ByEmail(command.email))
                .mapLeft(LoginInError::FindFailed)
                .bind()

            ensureNotNull(user) { LoginInError.InvalidCredentials }

            val passwordMatches = verifyPasswordPort
                .invoke(command.password, user.passwordHash)
                .mapLeft(LoginInError::PasswordVerificationFailed)
                .bind()

            ensure(passwordMatches) { LoginInError.InvalidCredentials }

            val jwt = issueJwtPort
                .invoke(user)
                .mapLeft(LoginInError::JwtIssuingFailed)
                .bind()

            LoginInResult(jwt = jwt)
        }
}
