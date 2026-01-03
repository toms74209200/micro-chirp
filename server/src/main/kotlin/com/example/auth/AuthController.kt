package com.example.auth

import com.example.api.AuthApi
import com.example.model.PostAuthLogin200Response
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
) : AuthApi {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    override fun postAuthLogin(): ResponseEntity<PostAuthLogin200Response> =
        when (val result = authService.generateUserId()) {
            is AuthResult.Success -> {
                val response = PostAuthLogin200Response(userId = result.userId)
                ResponseEntity.ok(response)
            }
            is AuthResult.Failure -> throw result.exception
        }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
