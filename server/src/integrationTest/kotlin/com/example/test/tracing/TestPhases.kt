package com.example.test.tracing

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer

class TestPhases internal constructor(
    private val tracer: Tracer?,
) {
    private var current: Span? = null

    fun arrange() = switchTo("arrange")

    fun act() = switchTo("act")

    fun assert() = switchTo("assert")

    private fun switchTo(name: String) {
        val tracer = tracer ?: return
        current?.end()
        current = tracer.spanBuilder(name).startSpan()
    }

    internal fun finish() {
        current?.end()
        current = null
    }
}
