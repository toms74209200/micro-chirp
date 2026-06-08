package com.example.test.tracing

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Injects [TestPhases] into `@Test` methods and, when [SpanTiming.enabled], records how long
 * the arrange/act/assert spans took for each test into [SpanTimingCollector].
 *
 * Spans are collected in-memory ([InMemorySpanExporter]) independently of the application's
 * `GlobalOpenTelemetry`, so nothing is exported over the network during tests.
 */
class SpanTimingExtension :
    BeforeEachCallback,
    AfterEachCallback,
    ParameterResolver {
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(SpanTimingExtension::class.java)
        private const val STORE_KEY = "phases"

        private val exporter: InMemorySpanExporter? by lazy {
            if (SpanTiming.enabled) InMemorySpanExporter.create() else null
        }

        private val tracer by lazy {
            exporter?.let { exporter ->
                OpenTelemetrySdk
                    .builder()
                    .setTracerProvider(
                        SdkTracerProvider
                            .builder()
                            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                            .build(),
                    ).build()
                    .getTracer("integration-test")
            }
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        exporter?.reset()
        context.getStore(NAMESPACE).put(STORE_KEY, TestPhases(tracer))
    }

    override fun afterEach(context: ExtensionContext) {
        val phases = context.getStore(NAMESPACE).get(STORE_KEY, TestPhases::class.java)!!
        phases.finish()

        val exporter = exporter ?: return
        val spans = exporter.finishedSpanItems

        fun durationNanos(name: String) = spans.filter { it.name == name }.sumOf { it.endEpochNanos - it.startEpochNanos }

        SpanTimingCollector.record(
            TestTiming(
                testClass = context.requiredTestClass.simpleName,
                testName = context.requiredTestMethod.name,
                arrangeNanos = durationNanos("arrange"),
                actNanos = durationNanos("act"),
                assertNanos = durationNanos("assert"),
            ),
        )
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean = parameterContext.parameter.type == TestPhases::class.java

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any = extensionContext.getStore(NAMESPACE).get(STORE_KEY, TestPhases::class.java)!!
}
