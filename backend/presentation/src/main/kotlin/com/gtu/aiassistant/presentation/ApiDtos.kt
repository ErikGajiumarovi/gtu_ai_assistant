package com.gtu.aiassistant.presentation

import com.gtu.aiassistant.domain.model.DomainError
import com.gtu.aiassistant.shared.ApiErrorResponse

fun fromDomainError(error: DomainError): ApiErrorResponse =
    ApiErrorResponse(
        code = error::class.simpleName ?: "domain_error",
        message = error.toString()
    )

fun fromUseCaseError(error: Any): ApiErrorResponse =
    ApiErrorResponse(
        code = error::class.simpleName ?: "use_case_error",
        message = error.toString()
    )

fun unauthorizedResponse(): ApiErrorResponse =
    ApiErrorResponse(
        code = "unauthorized",
        message = "Missing or invalid bearer token"
    )
