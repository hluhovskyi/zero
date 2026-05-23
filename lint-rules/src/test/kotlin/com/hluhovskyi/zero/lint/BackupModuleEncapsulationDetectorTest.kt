package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class BackupModuleEncapsulationDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = BackupModuleEncapsulationDetector()
    override fun getIssues(): List<Issue> = listOf(BackupModuleEncapsulationDetector.ISSUE)

    private val okhttpStub = kotlin(
        "stubs/okhttp3/OkHttpClient.kt",
        """
        package okhttp3
        class OkHttpClient
        """,
    ).indented()

    private val androidContextStub = kotlin(
        "stubs/android/content/Context.kt",
        """
        package android.content
        class Context
        """,
    ).indented()

    private val androidxKeyStoreStub = kotlin(
        "stubs/androidx/security/crypto/MasterKey.kt",
        """
        package androidx.security.crypto
        class MasterKey
        """,
    ).indented()

    private val serializationStub = kotlin(
        "stubs/kotlinx/serialization/json/Json.kt",
        """
        package kotlinx.serialization.json
        class Json
        """,
    ).indented()

    fun `test flags zero-backup file importing okhttp3`() {
        lint()
            .files(
                okhttpStub,
                kotlin(
                    "../zero-backup/src/main/java/com/hluhovskyi/zero/backup/Bad.kt",
                    """
                    package com.hluhovskyi.zero.backup
                    import okhttp3.OkHttpClient
                    class Bad { val client: OkHttpClient? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("BackupModuleEncapsulation")
    }

    fun `test flags zero-backup file importing android`() {
        lint()
            .files(
                androidContextStub,
                kotlin(
                    "../zero-backup/src/main/java/com/hluhovskyi/zero/backup/Bad.kt",
                    """
                    package com.hluhovskyi.zero.backup
                    import android.content.Context
                    class Bad { val ctx: Context? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("BackupModuleEncapsulation")
    }

    fun `test flags zero-backup file importing androidx`() {
        lint()
            .files(
                androidxKeyStoreStub,
                kotlin(
                    "../zero-backup/src/main/java/com/hluhovskyi/zero/backup/Bad.kt",
                    """
                    package com.hluhovskyi.zero.backup
                    import androidx.security.crypto.MasterKey
                    class Bad { val key: MasterKey? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("BackupModuleEncapsulation")
    }

    fun `test allows zero-backup file with kotlinx serialization`() {
        lint()
            .files(
                serializationStub,
                kotlin(
                    "../zero-backup/src/main/java/com/hluhovskyi/zero/backup/Ok.kt",
                    """
                    package com.hluhovskyi.zero.backup
                    import kotlinx.serialization.json.Json
                    class Ok { val json: Json? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test ignores files outside zero-backup`() {
        lint()
            .files(
                okhttpStub,
                kotlin(
                    "../zero-remote/src/main/java/com/hluhovskyi/zero/remote/Ok.kt",
                    """
                    package com.hluhovskyi.zero.remote
                    import okhttp3.OkHttpClient
                    class Ok { val client: OkHttpClient? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
