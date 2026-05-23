package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class TestBridgeBoundaryDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = TestBridgeBoundaryDetector()
    override fun getIssues(): List<Issue> = listOf(TestBridgeBoundaryDetector.ISSUE)

    private val prodRepoStub = kotlin(
        "src/main/java/com/hluhovskyi/zero/accounts/AccountRepository.kt",
        """
        package com.hluhovskyi.zero.accounts
        class AccountRepository
        """,
    ).indented()

    private val testBridgeStub = kotlin(
        "src/main/java/com/hluhovskyi/zero/testbridge/DatabaseTestBridge.kt",
        """
        package com.hluhovskyi.zero.testbridge
        interface DatabaseTestBridge
        """,
    ).indented()

    private val mainActivityStub = kotlin(
        "src/main/java/com/hluhovskyi/zero/activity/MainActivity.kt",
        """
        package com.hluhovskyi.zero.activity
        class MainActivity
        """,
    ).indented()

    private val otherAndroidTestStub = kotlin(
        "src/androidTest/java/com/hluhovskyi/zero/robots/TransactionsRobot.kt",
        """
        package com.hluhovskyi.zero.robots
        class TransactionsRobot
        """,
    ).indented()

    fun `test flags androidTest import of production repository`() {
        lint()
            .files(
                prodRepoStub,
                kotlin(
                    "src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.accounts.AccountRepository
                    class SomeTest { val repo: AccountRepository? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("TestBridgeBoundary")
    }

    fun `test allows androidTest import of zero-test-bridge type`() {
        lint()
            .files(
                testBridgeStub,
                kotlin(
                    "src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.testbridge.DatabaseTestBridge
                    class SomeTest { val bridge: DatabaseTestBridge? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows androidTest import of MainActivity`() {
        lint()
            .files(
                mainActivityStub,
                kotlin(
                    "src/androidTest/java/com/hluhovskyi/zero/SomeTest.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.activity.MainActivity
                    class SomeTest { val activity: MainActivity? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows androidTest robot importing another androidTest robot`() {
        lint()
            .files(
                otherAndroidTestStub,
                kotlin(
                    "src/androidTest/java/com/hluhovskyi/zero/AnotherRobot.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.robots.TransactionsRobot
                    class AnotherRobot { val r: TransactionsRobot? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test ignores files outside androidTest source set`() {
        lint()
            .files(
                prodRepoStub,
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/SomeProdFile.kt",
                    """
                    package com.hluhovskyi.zero
                    import com.hluhovskyi.zero.accounts.AccountRepository
                    class SomeProdFile { val repo: AccountRepository? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
