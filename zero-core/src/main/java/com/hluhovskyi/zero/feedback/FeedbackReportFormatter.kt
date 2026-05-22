package com.hluhovskyi.zero.feedback

import com.hluhovskyi.zero.common.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class FeedbackReportFormatter(
    private val deviceInfo: DeviceInfo,
    private val isDebugBuild: Boolean,
    private val clock: Clock,
) {

    fun format(type: FeedbackType, description: String, snapshot: Breadcrumbs.Snapshot): FeedbackReport {
        val title = title(description)
        val body = body(description, snapshot)
        return FeedbackReport(title = title, body = body, type = type, isDebug = isDebugBuild)
    }

    private fun title(description: String): String {
        val firstLine = description.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.replace("`", "")
            ?.trim()
        return if (firstLine.isNullOrBlank()) {
            "Feedback from ${sanitize(deviceInfo.manufacturer)} ${sanitize(deviceInfo.model)}".trim()
        } else {
            firstLine.take(80)
        }
    }

    private fun body(description: String, snapshot: Breadcrumbs.Snapshot): String = buildString {
        appendLine("## Description")
        appendLine()
        val fence = "`".repeat(maxOf(3, longestBacktickRun(description) + 1))
        appendLine(fence)
        appendLine(description)
        appendLine(fence)
        appendLine()
        appendLine("## Device")
        appendLine()
        appendLine("- Device: ${sanitize(deviceInfo.model)} (${sanitize(deviceInfo.manufacturer)})")
        appendLine("- OS: Android ${sanitize(deviceInfo.osVersion)} (SDK ${deviceInfo.sdkInt})")
        appendLine("- App: ${sanitize(deviceInfo.versionName)} (${deviceInfo.versionCode})")
        if (snapshot.navigation.isNotEmpty() || snapshot.breadcrumbs.isNotEmpty()) {
            appendLine()
            appendLine("<details>")
            appendLine("<summary>Diagnostics</summary>")
            appendLine()
            if (snapshot.navigation.isNotEmpty()) {
                appendLine("### Navigation")
                appendLine()
                snapshot.navigation.forEach { entry ->
                    appendLine("- ${formatTimestamp(entry)}  ${entry.message}")
                }
                appendLine()
            }
            if (snapshot.breadcrumbs.isNotEmpty()) {
                appendLine("### Breadcrumbs")
                appendLine()
                snapshot.breadcrumbs.forEach { entry ->
                    appendLine("- ${formatTimestamp(entry)}  ${entry.message}")
                }
                appendLine()
            }
            appendLine("</details>")
        }
    }

    private fun formatTimestamp(entry: Breadcrumbs.Entry): String {
        val dt = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val ms = entry.timestamp.toEpochMilliseconds() % 1000
        return "%02d:%02d:%02d.%03d".format(dt.hour, dt.minute, dt.second, ms)
    }

    private fun longestBacktickRun(text: String): Int {
        var max = 0
        var run = 0
        for (c in text) {
            if (c == '`') {
                run++
                if (run > max) max = run
            } else {
                run = 0
            }
        }
        return max
    }

    private fun sanitize(value: String): String = value.map { c ->
        if (c.isLetterOrDigit() || c == ' ' || c == '.' || c == '_' || c == '-') c else '?'
    }.joinToString("")
}
