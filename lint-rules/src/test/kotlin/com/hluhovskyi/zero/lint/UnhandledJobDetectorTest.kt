package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class UnhandledJobDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = UnhandledJobDetector()
    override fun getIssues(): List<Issue> = listOf(UnhandledJobDetector.ISSUE)

    // Provide kotlinx.coroutines.Job so resolve() works in the lint test environment.
    private val jobJavaStub = java(
        """
        package kotlinx.coroutines;
        public interface Job {
            void cancel();
        }
        """,
    ).indented()

    private val coroutineScopeStub = kotlin(
        """
        package kotlinx.coroutines
        import kotlinx.coroutines.Job
        class CoroutineScope
        fun CoroutineScope.launch(block: () -> Unit): Job = error("stub")
        fun captureJob(block: () -> Job): Job = block()
        """,
    ).indented()

    fun `test flags non-last explicit-receiver launch in method body`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.launch
                    fun example(scope: CoroutineScope) {
                        scope.launch { }
                        scope.launch { }
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UnhandledJob")
    }

    fun `test flags first of two explicit-receiver launches in lambda`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.Job
                    import kotlinx.coroutines.captureJob
                    import kotlinx.coroutines.launch
                    fun example(scope: CoroutineScope): Job = captureJob {
                        scope.launch { }
                        scope.launch { }
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("UnhandledJob")
    }

    fun `test clean when single explicit-receiver launch is sole expression in method body`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.launch
                    fun example(scope: CoroutineScope) {
                        scope.launch { }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when job is implicit receiver launch inside coroutine scope`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.launch
                    fun CoroutineScope.example() {
                        launch { }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when job is last expression of job-returning lambda`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.Job
                    import kotlinx.coroutines.captureJob
                    import kotlinx.coroutines.launch
                    fun example(scope: CoroutineScope): Job = captureJob {
                        scope.launch { }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when job is implicit return of job-returning method`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.Job
                    import kotlinx.coroutines.launch
                    fun startWork(scope: CoroutineScope): Job {
                        scope.launch { }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when job result is immediately used via chained call`() {
        lint()
            .files(
                jobJavaStub,
                coroutineScopeStub,
                kotlin(
                    """
                    import kotlinx.coroutines.CoroutineScope
                    import kotlinx.coroutines.launch
                    fun example(scope: CoroutineScope) {
                        scope.launch { }.cancel()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
