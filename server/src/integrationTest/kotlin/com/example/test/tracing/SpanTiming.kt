package com.example.test.tracing

object SpanTiming {
    val enabled: Boolean = System.getProperty("spanTiming.enabled").toBoolean()
}
