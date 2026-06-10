package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class DirectClockUsageDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = DirectClockUsageDetector()
    override fun getIssues(): List<Issue> = listOf(DirectClockUsageDetector.ISSUE)

    private val datetimeStub = kotlin(
        "stubs/kotlinx/datetime/Clock.kt",
        """
        package kotlinx.datetime
        object Clock {
            object System {
                fun now(): Long = 0L
            }
        }
        """,
    ).indented()

    fun `test flags Clock System in feature code`() {
        lint()
            .files(
                datetimeStub,
                kotlin(
                    "../zero-core/src/main/java/com/hluhovskyi/zero/backup/Bad.kt",
                    """
                    package com.hluhovskyi.zero.backup
                    import kotlinx.datetime.Clock
                    class Bad {
                        fun stamp(): Long = Clock.System.now()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("DirectClockUsage")
    }

    fun `test allows Clock System in common time implementations`() {
        lint()
            .files(
                datetimeStub,
                kotlin(
                    "../app/src/main/java/com/hluhovskyi/zero/common/time/ZoneBasedClock.kt",
                    """
                    package com.hluhovskyi.zero.common.time
                    import kotlinx.datetime.Clock
                    class ZoneBasedClock {
                        fun now(): Long = Clock.System.now()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows Clock System in test bridge`() {
        lint()
            .files(
                datetimeStub,
                kotlin(
                    "../zero-test-bridge/src/main/java/com/hluhovskyi/zero/testbridge/DefaultDatabaseTestBridge.kt",
                    """
                    package com.hluhovskyi.zero.testbridge
                    import kotlinx.datetime.Clock
                    class DefaultDatabaseTestBridge {
                        fun stamp(): Long = Clock.System.now()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
