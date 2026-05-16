package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class TestBridgeProductionPurityDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = TestBridgeProductionPurityDetector()
    override fun getIssues(): List<Issue> = listOf(TestBridgeProductionPurityDetector.ISSUE)

    private val junitStub = kotlin(
        "stubs/org/junit/Test.kt",
        """
        package org.junit
        annotation class Test
        """,
    ).indented()

    private val androidxTestStub = kotlin(
        "stubs/androidx/test/core/app/ApplicationProvider.kt",
        """
        package androidx.test.core.app
        class ApplicationProvider
        """,
    ).indented()

    fun `test flags zero-test-bridge file importing org junit`() {
        lint()
            .files(
                junitStub,
                kotlin(
                    "../zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/Bad.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import org.junit.Test
                    class Bad { val t: Test? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("TestBridgeProductionPurity")
    }

    fun `test flags zero-test-bridge file importing androidx test`() {
        lint()
            .files(
                androidxTestStub,
                kotlin(
                    "../zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/Bad.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import androidx.test.core.app.ApplicationProvider
                    class Bad { val p: ApplicationProvider? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("TestBridgeProductionPurity")
    }

    fun `test allows zero-test-bridge file with production-only imports`() {
        lint()
            .files(
                kotlin(
                    "../zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/Ok.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import kotlin.collections.List
                    class Ok { val xs: List<String> = emptyList() }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test ignores files outside zero-test-bridge main source set`() {
        lint()
            .files(
                junitStub,
                kotlin(
                    "src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import org.junit.Test
                    class SomeTest { val t: Test? = null }
                    """,
                ).indented(),
            )
            .allowCompilationErrors()
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
