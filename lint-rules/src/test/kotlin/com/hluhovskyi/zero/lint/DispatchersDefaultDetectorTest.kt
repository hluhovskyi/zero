package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class DispatchersDefaultDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = DispatchersDefaultDetector()
    override fun getIssues(): List<Issue> = listOf(DispatchersDefaultDetector.ISSUE)

    private val dispatchersStub = kotlin(
        "stubs/kotlinx/coroutines/Dispatchers.kt",
        """
        package kotlinx.coroutines
        object Dispatchers {
            val Default: Any = Any()
            val IO: Any = Any()
        }
        """,
    ).indented()

    fun `test flags Dispatchers Default in feature code`() {
        lint()
            .files(
                dispatchersStub,
                kotlin(
                    "../zero-core/src/main/java/com/hluhovskyi/zero/feedback/Bad.kt",
                    """
                    package com.hluhovskyi.zero.feedback
                    import kotlinx.coroutines.Dispatchers
                    class Bad {
                        val dispatcher: Any = Dispatchers.Default
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("NoDispatchersDefault")
    }

    fun `test allows Dispatchers Default in dispatcher provider`() {
        lint()
            .files(
                dispatchersStub,
                kotlin(
                    "../app/src/main/java/com/hluhovskyi/zero/common/coroutines/KotlinDispatcherProvider.kt",
                    """
                    package com.hluhovskyi.zero.common.coroutines
                    import kotlinx.coroutines.Dispatchers
                    class KotlinDispatcherProvider {
                        fun cpu(): Any = Dispatchers.Default
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows Dispatchers IO`() {
        lint()
            .files(
                dispatchersStub,
                kotlin(
                    "../zero-core/src/main/java/com/hluhovskyi/zero/feedback/Ok.kt",
                    """
                    package com.hluhovskyi.zero.feedback
                    import kotlinx.coroutines.Dispatchers
                    class Ok {
                        val dispatcher: Any = Dispatchers.IO
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
