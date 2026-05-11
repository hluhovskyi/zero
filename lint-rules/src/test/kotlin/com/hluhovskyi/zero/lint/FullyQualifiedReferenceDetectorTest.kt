package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestMode
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class FullyQualifiedReferenceDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = FullyQualifiedReferenceDetector()
    override fun getIssues(): List<Issue> = listOf(FullyQualifiedReferenceDetector.ISSUE)

    private val barStub = java(
        """
        package com.example.foo;
        public class Bar {
            public static final int VALUE = 42;
            public static Bar instance() { return new Bar(); }
        }
        """,
    ).indented()

    fun `test flags constructor call via fully-qualified name`() {
        lint()
            .files(
                barStub,
                kotlin(
                    """
                    fun test() {
                        val x = com.example.foo.Bar()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("FullyQualifiedReference")
    }

    fun `test flags static member access via fully-qualified class`() {
        lint()
            .files(
                barStub,
                kotlin(
                    """
                    fun test(): Int = com.example.foo.Bar.VALUE
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("FullyQualifiedReference")
    }

    fun `test flags method call via fully-qualified class`() {
        lint()
            .files(
                barStub,
                kotlin(
                    """
                    fun test() = com.example.foo.Bar.instance()
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectContains("FullyQualifiedReference")
    }

    fun `test clean when class is imported and short name is used`() {
        lint()
            .files(
                barStub,
                kotlin(
                    """
                    import com.example.foo.Bar
                    fun test() {
                        val x = Bar()
                    }
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for regular method chain on a non-package receiver`() {
        lint()
            .files(
                barStub,
                kotlin(
                    """
                    import com.example.foo.Bar
                    fun test(bar: Bar): Bar = bar.instance()
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }

    fun `test clean for enum member access`() {
        lint()
            .files(
                java(
                    """
                    package com.example.foo;
                    public enum Color { RED, GREEN, BLUE }
                    """,
                ).indented(),
                kotlin(
                    """
                    import com.example.foo.Color
                    fun test(): Color = Color.RED
                    """,
                ).indented(),
            )
            .testModes(TestMode.DEFAULT)
            .run()
            .expectClean()
    }
}
