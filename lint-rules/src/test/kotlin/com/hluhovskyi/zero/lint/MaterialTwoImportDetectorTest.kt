package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class MaterialTwoImportDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = MaterialTwoImportDetector()
    override fun getIssues(): List<Issue> = listOf(MaterialTwoImportDetector.ISSUE)

    private val m2Stub = kotlin(
        "stubs/androidx/compose/material/Scaffold.kt",
        """
        package androidx.compose.material
        class Scaffold
        """,
    ).indented()

    private val m3Stub = kotlin(
        "stubs/androidx/compose/material3/Scaffold.kt",
        """
        package androidx.compose.material3
        class Scaffold
        """,
    ).indented()

    private val iconsStub = kotlin(
        "stubs/androidx/compose/material/icons/Icons.kt",
        """
        package androidx.compose.material.icons
        object Icons
        """,
    ).indented()

    fun `test flags material2 import in feature code`() {
        lint()
            .files(
                m2Stub,
                kotlin(
                    "../zero-core/src/main/java/com/hluhovskyi/zero/settings/Bad.kt",
                    """
                    package com.hluhovskyi.zero.settings
                    import androidx.compose.material.Scaffold
                    class Bad { val s: Scaffold? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("MaterialTwoImport")
    }

    fun `test allows material3 import`() {
        lint()
            .files(
                m3Stub,
                kotlin(
                    "../zero-core/src/main/java/com/hluhovskyi/zero/settings/Ok.kt",
                    """
                    package com.hluhovskyi.zero.settings
                    import androidx.compose.material3.Scaffold
                    class Ok { val s: Scaffold? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows material icons import`() {
        lint()
            .files(
                iconsStub,
                kotlin(
                    "../zero-core/src/main/java/com/hluhovskyi/zero/settings/Icons.kt",
                    """
                    package com.hluhovskyi.zero.settings
                    import androidx.compose.material.icons.Icons
                    class Ok { val icons: Icons = Icons }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test allows material2 in bottom sheet island`() {
        lint()
            .files(
                m2Stub,
                kotlin(
                    "../app/src/main/java/com/hluhovskyi/zero/activity/screens/MainActivityScreenViewProvider.kt",
                    """
                    package com.hluhovskyi.zero.activity.screens
                    import androidx.compose.material.Scaffold
                    class MainActivityScreenViewProvider { val s: Scaffold? = null }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
