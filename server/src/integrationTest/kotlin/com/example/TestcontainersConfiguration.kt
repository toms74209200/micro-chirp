package com.example

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer() = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

    @Bean
    fun postsMvInitializer(dataSource: DataSource) =
        ApplicationRunner {
            ResourceDatabasePopulator(ClassPathResource("posts_mv.sql")).execute(dataSource)
        }
}
