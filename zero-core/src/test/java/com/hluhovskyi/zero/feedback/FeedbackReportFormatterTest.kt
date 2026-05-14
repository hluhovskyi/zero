package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.time.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackReportFormatterTest {

    private val fixedInstant = Instant.parse("2024-06-01T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val deviceInfo = DeviceInfo(
        manufacturer = "Google",
        model = "Pixel 8",
        osVersion = "14",
        sdkInt = 34,
        versionName = "1.4.2",
        versionCode = 142L,
    )

    private fun formatter(isDebugBuild: Boolean = false): FeedbackReportFormatter = FeedbackReportFormatter(deviceInfo = deviceInfo, isDebugBuild = isDebugBuild, clock = clock)

    private val emptySnapshot = Breadcrumbs.Snapshot(navigation = emptyList(), breadcrumbs = emptyList())

    @Test
    fun `title is first non-blank line truncated to 80`() {
        val description = "\n\nFAB crash on edit\nMore details below\n"
        val report = formatter().format(description, emptySnapshot)
        assertEquals("FAB crash on edit", report.title)
    }

    @Test
    fun `title falls back to device when description blank`() {
        val report = formatter().format("   ", emptySnapshot)
        assertEquals("Feedback from Google Pixel 8", report.title)
    }

    @Test
    fun `title truncates long first line to 80 chars`() {
        val longLine = "x".repeat(100)
        val report = formatter().format(longLine, emptySnapshot)
        assertEquals(80, report.title.length)
    }

    @Test
    fun `description containing triple backticks gets a 4-backtick fence`() {
        val description = "see ```kotlin\\nval x=1\\n```"
        val report = formatter().format(description, emptySnapshot)
        assertTrue("body must contain 4-backtick fence", report.body.contains("````\n"))
    }

    @Test
    fun `description containing 4 backticks gets a 5-backtick fence`() {
        val description = "see \\n```` block ````"
        val report = formatter().format(description, emptySnapshot)
        assertTrue("body must contain 5-backtick fence", report.body.contains("`````\n"))
    }

    @Test
    fun `device-info sanitiser replaces injection chars with question marks`() {
        val evil = deviceInfo.copy(manufacturer = "[evil](http://x)")
        val formatter = FeedbackReportFormatter(deviceInfo = evil, isDebugBuild = false, clock = clock)
        val report = formatter.format("desc", emptySnapshot)
        assertTrue(
            "expected sanitised manufacturer in body, got:\n${report.body}",
            report.body.contains("?evil??http???x?"),
        )
    }

    @Test
    fun `labels include feedback always`() {
        val report = formatter(isDebugBuild = false).format("desc", emptySnapshot)
        assertEquals(listOf("feedback"), report.labels)
    }

    @Test
    fun `labels include debug when isDebugBuild`() {
        val report = formatter(isDebugBuild = true).format("desc", emptySnapshot)
        assertEquals(listOf("feedback", "debug"), report.labels)
    }

    @Test
    fun `body omits diagnostics block when both rings empty`() {
        val report = formatter().format("desc", emptySnapshot)
        assertFalse("expected no <details> when no diagnostics", report.body.contains("<details>"))
    }

    @Test
    fun `body includes navigation block when navigation populated and omits breadcrumbs when empty`() {
        val snapshot = Breadcrumbs.Snapshot(
            navigation = listOf(Breadcrumbs.Entry(fixedInstant, "transactions/all")),
            breadcrumbs = emptyList(),
        )
        val report = formatter().format("desc", snapshot)
        assertTrue(report.body.contains("<details>"))
        assertTrue(report.body.contains("### Navigation"))
        assertFalse("breadcrumbs section should be omitted", report.body.contains("### Breadcrumbs"))
    }
}
