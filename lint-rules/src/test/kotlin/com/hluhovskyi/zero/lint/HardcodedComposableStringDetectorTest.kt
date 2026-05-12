package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class HardcodedComposableStringDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = HardcodedComposableStringDetector()
    override fun getIssues(): List<Issue> = listOf(HardcodedComposableStringDetector.ISSUE)

    private val composableStub = kotlin(
        """
        package androidx.compose.runtime
        annotation class Composable
        """,
    ).indented()

    fun `test flags positional Text argument`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("Save") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("HardcodedComposableString")
    }

    fun `test flags named text argument`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text(text = "Save") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("HardcodedComposableString")
    }

    fun `test flags contentDescription literal`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Icon(contentDescription = "Close") }
                    fun Icon(contentDescription: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("HardcodedComposableString")
    }

    fun `test clean when text uses stringResource`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text(text = stringResource(1)) }
                    fun Text(text: String) {}
                    fun stringResource(id: Int): String = ""
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean when Text called outside composable`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    fun notComposable() { Text("Save") }
                    fun Text(text: String) {}
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
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for single-char string`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("x") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for pure-numeric string`() {
        lint()
            .files(
                composableStub,
                kotlin(
                    """
                    import androidx.compose.runtime.Composable
                    @Composable fun Screen() { Text("0") }
                    fun Text(text: String) {}
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
