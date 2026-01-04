package com.example.auth

import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

sealed interface AuthResult {
    data class Success(
        val userId: UUID,
    ) : AuthResult

    data class Failure(
        val exception: Exception,
    ) : AuthResult
}

@Service
class AuthService(
    private val userRepository: UserRepository,
) {
    @WithSpan
    fun generateUserId(): AuthResult {
        val userId = UUID.randomUUID()
        val user = User(id = userId, createdAt = Instant.now())

        return try {
            userRepository.save(user)
            AuthResult.Success(userId)
        } catch (e: DataAccessException) {
            AuthResult.Failure(e)
        }
    }
}
