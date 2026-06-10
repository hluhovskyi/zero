package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class ZonedClockPreferredDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = ZonedClockPreferredDetector()
    override fun getIssues(): List<Issue> = listOf(ZonedClockPreferredDetector.ISSUE)

    private val timeStubs = kotlin(
        """
        package com.hluhovskyi.zero.common.time
        interface Clock
        interface ZoneProvider
        interface ZonedClock : Clock, ZoneProvider
        """,
    ).indented()

    fun `test flags constructor taking both Clock and ZoneProvider`() {
        lint()
            .files(
                timeStubs,
                kotlin(
                    """
                    package com.hluhovskyi.zero.sample
                    import com.hluhovskyi.zero.common.time.Clock
                    import com.hluhovskyi.zero.common.time.ZoneProvider
                    class Sample(private val clock: Clock, private val zoneProvider: ZoneProvider)
                    """,
                ).indented(),
            )
            .run()
            .expectContains("ZonedClockPreferred")
    }

    fun `test clean when only Clock is taken`() {
        lint()
            .files(
                timeStubs,
                kotlin(
                    """
                    package com.hluhovskyi.zero.sample
                    import com.hluhovskyi.zero.common.time.Clock
                    class Sample(private val clock: Clock)
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when ZonedClock is already used`() {
        lint()
            .files(
                timeStubs,
                kotlin(
                    """
                    package com.hluhovskyi.zero.sample
                    import com.hluhovskyi.zero.common.time.ZonedClock
                    class Sample(private val zonedClock: ZonedClock)
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
