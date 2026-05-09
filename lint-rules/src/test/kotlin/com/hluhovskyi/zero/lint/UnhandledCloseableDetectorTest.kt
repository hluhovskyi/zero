package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class UnhandledCloseableDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = UnhandledCloseableDetector()
    override fun getIssues(): List<Issue> = listOf(UnhandledCloseableDetector.ISSUE)

    // Provide java.io.Closeable so resolve() works in the lint test environment.
    private val closeableJavaStub = java(
        """
        package java.io;
        public interface Closeable {
            void close();
        }
        """,
    ).indented()

    // language=kotlin
    private val closeableStub = kotlin(
        """
        package stubs
        import java.io.Closeable
        fun makeCloseable(): Closeable = object : Closeable { override fun close() {} }
        fun consume(c: Closeable) { c.close() }
        fun withCloseable(block: () -> Closeable): Closeable = block()
        """,
    ).indented()

    fun `test flags closeable discarded as standalone statement`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import stubs.makeCloseable
                    fun example() {
                        makeCloseable()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UnhandledCloseable")
    }

    fun `test flags non-last closeable in block`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import stubs.makeCloseable
                    fun example() {
                        makeCloseable()
                        println("hello")
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UnhandledCloseable")
    }

    fun `test clean when closeable is assigned`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import stubs.makeCloseable
                    fun example() {
                        val c = makeCloseable()
                        c.close()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when closeable is passed directly as argument`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import stubs.makeCloseable
                    import stubs.consume
                    fun example() {
                        consume(makeCloseable())
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when closeable is last expression in lambda`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import stubs.makeCloseable
                    import stubs.withCloseable
                    fun example(): java.io.Closeable = withCloseable { makeCloseable() }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when closeable is implicit return of closeable-returning method`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import java.io.Closeable
                    import stubs.makeCloseable
                    fun wrap(): Closeable {
                        makeCloseable()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test flags closeable discarded inside lambda before last expression`() {
        lint()
            .files(
                closeableJavaStub,
                closeableStub,
                kotlin(
                    """
                    import java.io.Closeable
                    import stubs.makeCloseable
                    fun withCloseable2(block: () -> Closeable): Closeable = block()
                    fun example(): Closeable = withCloseable2 {
                        makeCloseable()
                        makeCloseable()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UnhandledCloseable")
    }
}
