package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class UppercaseStringResourceDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = UppercaseStringResourceDetector()
    override fun getIssues(): List<Issue> = listOf(UppercaseStringResourceDetector.ISSUE)

    fun `test flags all-caps string`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">SAVE</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UppercaseStringResource")
    }

    fun `test flags mixed-case all-caps letters`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">ASSETS</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UppercaseStringResource")
    }

    fun `test clean for sentence-case string`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">Save</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for lower-case string`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">save</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for single uppercase letter`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">A</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for numeric string`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">100</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for empty string`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label"></string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test flags all-caps with punctuation`() {
        lint()
            .files(
                xml(
                    "res/values/strings.xml",
                    """
                    <resources>
                        <string name="label">NET WORTH</string>
                    </resources>
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UppercaseStringResource")
    }
}
