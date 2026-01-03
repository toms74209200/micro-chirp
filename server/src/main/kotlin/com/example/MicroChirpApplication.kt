package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MicroChirpApplication

fun main(args: Array<String>) {
    runApplication<MicroChirpApplication>(*args)
}
