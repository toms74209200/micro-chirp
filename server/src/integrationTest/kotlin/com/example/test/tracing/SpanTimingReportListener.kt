package com.example.test.tracing

import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import java.io.File
import java.util.Locale

private const val TEST_HEADER = "Test"
private const val ARRANGE_HEADER = "Arrange (ms)"
private const val ACT_HEADER = "Act (ms)"
private const val ASSERT_HEADER = "Assert (ms)"
private const val TOTAL_HEADER = "Total (ms)"
private const val MAX_TEST_COLUMN_WIDTH = 60

private val REPORT_FILE = File("build/reports/spanTiming/spanTiming.txt")

class SpanTimingReportListener : TestExecutionListener {
    override fun testPlanExecutionFinished(testPlan: TestPlan) {
        if (!SpanTiming.enabled) return
        val timings = SpanTimingCollector.all()
        if (timings.isEmpty()) return

        val table = renderTable(timings)
        println(table)

        REPORT_FILE.parentFile.mkdirs()
        REPORT_FILE.writeText(table)
    }

    private fun renderTable(timings: List<TestTiming>): String {
        data class Row(
            val test: String,
            val arrangeMs: Double,
            val actMs: Double,
            val assertMs: Double,
            val totalMs: Double,
        )

        fun fmt(value: Double) = String.format(Locale.ROOT, "%.2f", value)

        val rows =
            timings
                .map { timing ->
                    val arrangeMs = timing.arrangeNanos / 1_000_000.0
                    val actMs = timing.actNanos / 1_000_000.0
                    val assertMs = timing.assertNanos / 1_000_000.0
                    Row(
                        test = "${timing.testClass} > ${timing.testName}",
                        arrangeMs = arrangeMs,
                        actMs = actMs,
                        assertMs = assertMs,
                        totalMs = arrangeMs + actMs + assertMs,
                    )
                }.sortedByDescending { it.totalMs }

        val testWidth = minOf(MAX_TEST_COLUMN_WIDTH, maxOf(TEST_HEADER.length, rows.maxOf { it.test.length }))
        val arrangeWidth = maxOf(ARRANGE_HEADER.length, rows.maxOf { fmt(it.arrangeMs).length })
        val actWidth = maxOf(ACT_HEADER.length, rows.maxOf { fmt(it.actMs).length })
        val assertWidth = maxOf(ASSERT_HEADER.length, rows.maxOf { fmt(it.assertMs).length })
        val totalWidth = maxOf(TOTAL_HEADER.length, rows.maxOf { fmt(it.totalMs).length })

        fun row(
            testLines: List<String>,
            arrange: String,
            act: String,
            assertValue: String,
            total: String,
        ) = testLines
            .mapIndexed { index, line ->
                val arrangeCell = if (index == 0) arrange else ""
                val actCell = if (index == 0) act else ""
                val assertCell = if (index == 0) assertValue else ""
                val totalCell = if (index == 0) total else ""
                "${line.padEnd(testWidth)} | ${arrangeCell.padStart(arrangeWidth)} | " +
                    "${actCell.padStart(actWidth)} | ${assertCell.padStart(assertWidth)} | ${totalCell.padStart(totalWidth)}"
            }.joinToString("\n")

        val header = row(listOf(TEST_HEADER), ARRANGE_HEADER, ACT_HEADER, ASSERT_HEADER, TOTAL_HEADER)
        val separator =
            header
                .lineSequence()
                .first()
                .map { if (it == '|') '+' else '-' }
                .joinToString("")
        val body =
            rows.joinToString("\n") {
                row(wrapTestName(it.test, testWidth), fmt(it.arrangeMs), fmt(it.actMs), fmt(it.assertMs), fmt(it.totalMs))
            }

        return listOf(header, separator, body).joinToString("\n")
    }

    private fun wrapTestName(
        name: String,
        width: Int,
    ): List<String> {
        if (name.length <= width) return listOf(name)

        val lines = mutableListOf<String>()
        var remaining = name
        while (remaining.length > width) {
            val breakAt = remaining.lastIndexOf(' ', width).takeIf { it > 0 } ?: width
            lines += remaining.substring(0, breakAt).trimEnd()
            remaining = remaining.substring(breakAt).trimStart()
        }
        lines += remaining
        return lines
    }
}
