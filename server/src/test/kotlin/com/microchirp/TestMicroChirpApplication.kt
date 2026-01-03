package com.microchirp

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<MicroChirpApplication>().with(TestcontainersConfiguration::class).run(*args)
}
