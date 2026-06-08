package com.example.test.tracing

import java.util.Collections

data class TestTiming(
    val testClass: String,
    val testName: String,
    val arrangeNanos: Long,
    val actNanos: Long,
    val assertNanos: Long,
)

object SpanTimingCollector {
    private val timings = Collections.synchronizedList(mutableListOf<TestTiming>())

    fun record(timing: TestTiming) {
        timings.add(timing)
    }

    fun all(): List<TestTiming> = timings.toList()
}
