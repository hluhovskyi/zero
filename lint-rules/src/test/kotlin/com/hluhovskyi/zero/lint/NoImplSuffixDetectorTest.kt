package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class NoImplSuffixDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = NoImplSuffixDetector()
    override fun getIssues(): List<Issue> = listOf(NoImplSuffixDetector.ISSUE)

    fun `test flags Impl-suffixed class`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.settings
                    interface FooViewModel
                    class FooViewModelImpl : FooViewModel
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("NoImplSuffix")
    }

    fun `test allows Default-prefixed class`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.settings
                    interface FooViewModel
                    internal class DefaultFooViewModel : FooViewModel
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
