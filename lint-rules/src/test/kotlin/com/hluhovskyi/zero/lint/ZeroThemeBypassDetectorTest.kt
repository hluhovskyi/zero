package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class ZeroThemeBypassDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = ZeroThemeBypassDetector()
    override fun getIssues(): List<Issue> = listOf(ZeroThemeBypassDetector.ISSUE)

    private val colorStub = kotlin(
        """
        package androidx.compose.ui.graphics
        class Color(val value: ULong = 0UL) {
            fun copy(alpha: Float = 0f): Color = this
            companion object {
                val White = Color()
                val Black = Color()
                val Red = Color()
                val Green = Color()
                val Blue = Color()
                val Gray = Color()
                val Yellow = Color()
                val Magenta = Color()
                val Cyan = Color()
                val Unspecified = Color()
                val Transparent = Color()
            }
        }
        fun Color(argb: Int): Color = Color()
        fun Color(argb: Long): Color = Color()
        fun Color(red: Float, green: Float, blue: Float): Color = Color()
        fun Color(red: Float, green: Float, blue: Float, alpha: Float): Color = Color()
        """,
    ).indented()

    fun `test flags Color hex Int literal`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Bg = Color(0xFFFFFFFF.toInt())
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test flags Color Long literal`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Bg = Color(0xFFAA0000L)
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test flags Color RGB triple`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Bg = Color(0.5f, 0.5f, 0.5f)
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test flags Color RGBA quad`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Bg = Color(0.5f, 0.5f, 0.5f, 0.5f)
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test flags Color White`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Fg = Color.White
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test flags Color Black copy alpha`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Scrim = Color.Black.copy(alpha = 0.32f)
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test clean Color Unspecified`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Tint = Color.Unspecified
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean Color Transparent`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Pill = Color.Transparent
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean inside theme package`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.ui.theme
                    import androidx.compose.ui.graphics.Color
                    val Primary = Color(0xFF000E2F.toInt())
                    val Fallback = Color.White
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean inside UiColorScheme file`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    "src/com/hluhovskyi/zero/ui/UiColorScheme.kt",
                    """
                    package com.hluhovskyi.zero.ui
                    import androidx.compose.ui.graphics.Color
                    val Default = Color(0xFF8E8E93.toInt())
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean inside common Colors converter file`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    "src/com/hluhovskyi/zero/ui/common/Colors.kt",
                    """
                    package com.hluhovskyi.zero.ui.common
                    import androidx.compose.ui.graphics.Color
                    fun toCompose(argb: Int): Color = Color(argb)
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test flags Colors filename in wrong package`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    "src/com/hluhovskyi/zero/feature/Colors.kt",
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    val Bg = Color(0xFFFFFFFF.toInt())
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("ZeroThemeBypass")
    }

    fun `test clean with Suppress annotation`() {
        lint()
            .files(
                colorStub,
                kotlin(
                    """
                    package com.hluhovskyi.zero.feature
                    import androidx.compose.ui.graphics.Color
                    @Suppress("ZeroThemeBypass")
                    val Bespoke = Color(0xFFFFF8F6.toInt())
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
