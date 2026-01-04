package com.example.auth

import com.example.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class AuthServiceTest {
    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `when generateUserId with no parameters then returns Success with new user ID`() {
        val result = authService.generateUserId()

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        val userId = (result as AuthResult.Success).userId
        assertThat(userId).isNotNull()

        val user = userRepository.findById(userId)
        assertThat(user.isPresent).isTrue()
        assertThat(user.get().id).isEqualTo(userId)
        assertThat(user.get().createdAt).isNotNull()
    }
}
