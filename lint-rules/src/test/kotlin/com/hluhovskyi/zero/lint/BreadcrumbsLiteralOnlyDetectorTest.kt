package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class BreadcrumbsLiteralOnlyDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = BreadcrumbsLiteralOnlyDetector()
    override fun getIssues(): List<Issue> = listOf(BreadcrumbsLiteralOnlyDetector.ISSUE)

    private val breadcrumbsStub = kotlin(
        """
        package com.hluhovskyi.zero.feedback

        interface Breadcrumbs {
            fun log(message: String)
        }
        """,
    ).indented()

    fun `test accepts a string literal`() {
        lint()
            .files(
                breadcrumbsStub,
                kotlin(
                    """
                    package x
                    import com.hluhovskyi.zero.feedback.Breadcrumbs
                    fun f(b: Breadcrumbs) { b.log("tapped fab") }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test accepts polyadic of literals`() {
        lint()
            .files(
                breadcrumbsStub,
                kotlin(
                    """
                    package x
                    import com.hluhovskyi.zero.feedback.Breadcrumbs
                    fun f(b: Breadcrumbs) { b.log("saved " + "transaction") }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test rejects string template with embedded expression`() {
        lint()
            .files(
                breadcrumbsStub,
                kotlin(
                    """
                    package x
                    import com.hluhovskyi.zero.feedback.Breadcrumbs
                    fun f(b: Breadcrumbs, name: String) { b.log("saved ${"$"}name") }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("BreadcrumbsLiteralOnly")
    }

    fun `test rejects concatenation with non-literal`() {
        lint()
            .files(
                breadcrumbsStub,
                kotlin(
                    """
                    package x
                    import com.hluhovskyi.zero.feedback.Breadcrumbs
                    fun f(b: Breadcrumbs, name: String) { b.log("saved " + name) }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("BreadcrumbsLiteralOnly")
    }

    fun `test rejects variable reference`() {
        lint()
            .files(
                breadcrumbsStub,
                kotlin(
                    """
                    package x
                    import com.hluhovskyi.zero.feedback.Breadcrumbs
                    fun f(b: Breadcrumbs) {
                        val msg = "tapped fab"
                        b.log(msg)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("BreadcrumbsLiteralOnly")
    }

    fun `test rejects method call result`() {
        lint()
            .files(
                breadcrumbsStub,
                kotlin(
                    """
                    package x
                    import com.hluhovskyi.zero.feedback.Breadcrumbs
                    fun makeMessage(): String = "hello"
                    fun f(b: Breadcrumbs) { b.log(makeMessage()) }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("BreadcrumbsLiteralOnly")
    }

    fun `test ignores unrelated log calls`() {
        lint()
            .files(
                kotlin(
                    """
                    package x
                    class Logger { fun log(msg: String) {} }
                    fun f(l: Logger, name: String) { l.log("saved " + name) }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
