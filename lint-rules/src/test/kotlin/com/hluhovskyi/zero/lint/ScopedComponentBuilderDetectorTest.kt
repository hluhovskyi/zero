package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class ScopedComponentBuilderDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = ScopedComponentBuilderDetector()
    override fun getIssues(): List<Issue> = listOf(ScopedComponentBuilderDetector.ISSUE)

    private val daggerStubs = kotlin(
        """
        package dagger

        annotation class Provides
        annotation class Component {
            annotation class Builder
        }
        """,
    ).indented()

    private val scopeStub = kotlin(
        """
        package javax.inject

        annotation class Scope
        """,
    ).indented()

    private val activityScopeStub = kotlin(
        """
        package x

        import javax.inject.Scope

        @Scope
        annotation class ActivityScope
        """,
    ).indented()

    private val componentStub = kotlin(
        """
        package x

        @dagger.Component
        abstract class FooComponent {
            @dagger.Component.Builder
            interface Builder
        }
        """,
    ).indented()

    fun `test rejects scoped Builder provider`() {
        lint()
            .files(
                daggerStubs,
                scopeStub,
                activityScopeStub,
                componentStub,
                kotlin(
                    """
                    package x

                    object Module {
                        @dagger.Provides
                        @ActivityScope
                        fun fooComponentBuilder(): FooComponent.Builder = TODO()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/x/Module.kt:5: Error: Component Builder providers must be unscoped. Scoping makes the Builder a shared mutable singleton; @BindsInstance values from one consumer leak into the next. See docs/agents/dependency-injection.md. [ScopedComponentBuilder]
                    @ActivityScope
                    ~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    fun `test accepts unscoped Builder provider`() {
        lint()
            .files(
                daggerStubs,
                scopeStub,
                componentStub,
                kotlin(
                    """
                    package x

                    object Module {
                        @dagger.Provides
                        fun fooComponentBuilder(): FooComponent.Builder = TODO()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test ignores scoped provider that does not return a Builder`() {
        lint()
            .files(
                daggerStubs,
                scopeStub,
                activityScopeStub,
                kotlin(
                    """
                    package x

                    class FooUseCase

                    object Module {
                        @dagger.Provides
                        @ActivityScope
                        fun fooUseCase(): FooUseCase = FooUseCase()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test ignores unscoped non-Builder provider`() {
        lint()
            .files(
                daggerStubs,
                kotlin(
                    """
                    package x

                    class FooUseCase

                    object Module {
                        @dagger.Provides
                        fun fooUseCase(): FooUseCase = FooUseCase()
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
