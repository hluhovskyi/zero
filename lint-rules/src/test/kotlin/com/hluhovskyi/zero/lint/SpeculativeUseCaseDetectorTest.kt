package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class SpeculativeUseCaseDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = SpeculativeUseCaseDetector()
    override fun getIssues(): List<Issue> = listOf(SpeculativeUseCaseDetector.ISSUE)

    fun `test flags single-method UseCase interface`() {
        lint()
            .files(
                kotlin(
                    """
                    package x

                    interface FooSaveUseCase {
                        suspend fun save(value: String)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/x/FooSaveUseCase.kt:3: Warning: Single-method *UseCase interface looks speculative. Inline the implementation into the VM (or call the repository directly) unless this UseCase has 2+ callers, crosses a Dagger scope, hosts non-trivial logic, or is a documented module contract. See docs/agents/architecture.md. [SpeculativeUseCase]
                interface FooSaveUseCase {
                ^
                0 errors, 1 warnings
                """.trimIndent(),
            )
    }

    fun `test flags single-method UseCase even with Noop nested object`() {
        lint()
            .files(
                kotlin(
                    """
                    package x

                    interface FooSaveUseCase {
                        suspend fun save(value: String)

                        object Noop : FooSaveUseCase {
                            override suspend fun save(value: String) = Unit
                        }
                    }
                    """,
                ).indented(),
            )
            .run()
            .expect(
                """
                src/x/FooSaveUseCase.kt:3: Warning: Single-method *UseCase interface looks speculative. Inline the implementation into the VM (or call the repository directly) unless this UseCase has 2+ callers, crosses a Dagger scope, hosts non-trivial logic, or is a documented module contract. See docs/agents/architecture.md. [SpeculativeUseCase]
                interface FooSaveUseCase {
                ^
                0 errors, 1 warnings
                """.trimIndent(),
            )
    }

    fun `test allows multi-method UseCase interface`() {
        lint()
            .files(
                kotlin(
                    """
                    package x

                    interface FooUseCase {
                        suspend fun save(value: String)
                        suspend fun replace(value: String)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test allows non-UseCase named interface`() {
        lint()
            .files(
                kotlin(
                    """
                    package x

                    interface FooSaver {
                        suspend fun save(value: String)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test allows ActionStateModel-based UseCase with single perform method`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.hluhovskyi.zero.common

                    interface ActionStateModel<A, S> {
                        fun perform(action: A)
                    }
                    """,
                ).indented(),
                kotlin(
                    """
                    package x

                    import com.hluhovskyi.zero.common.ActionStateModel

                    interface FilterUseCase : ActionStateModel<FilterUseCase.Action, FilterUseCase.State> {
                        sealed interface Action
                        sealed interface State
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test respects @Suppress on speculative interface`() {
        lint()
            .files(
                kotlin(
                    """
                    package x

                    @Suppress("SpeculativeUseCase")
                    interface FooSaveUseCase {
                        suspend fun save(value: String)
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test ignores classes, only flags interfaces`() {
        lint()
            .files(
                kotlin(
                    """
                    package x

                    class FooSaveUseCase {
                        suspend fun save(value: String) = Unit
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
