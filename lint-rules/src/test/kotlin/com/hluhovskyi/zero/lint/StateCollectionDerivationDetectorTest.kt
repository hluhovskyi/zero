package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class StateCollectionDerivationDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = StateCollectionDerivationDetector()
    override fun getIssues(): List<Issue> = listOf(StateCollectionDerivationDetector.ISSUE)

    fun `test flags any() on state collection inside a ViewProvider`() {
        lint()
            .files(
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/foo/FooViewProvider.kt",
                    """
                    package com.hluhovskyi.zero.foo
                    data class State(val items: List<String>)
                    fun render(state: State): Boolean = state.items.any { it.isNotEmpty() }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("ViewProviderDerivation")
    }

    fun `test flags firstOrNull, count, sumOf, sortedBy`() {
        lint()
            .files(
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/foo/FooViewProvider.kt",
                    """
                    package com.hluhovskyi.zero.foo
                    data class Row(val n: Int, val id: String)
                    data class State(val rows: List<Row>)
                    fun a(state: State) = state.rows.firstOrNull { it.id == "x" }
                    fun b(state: State) = state.rows.count { it.n > 0 }
                    fun c(state: State) = state.rows.sumOf { it.n }
                    fun d(state: State) = state.rows.sortedBy { it.n }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("ViewProviderDerivation")
    }

    fun `test flags derivation on a destructured local (no state in chain)`() {
        lint()
            .files(
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/foo/FooViewProvider.kt",
                    """
                    package com.hluhovskyi.zero.foo
                    fun render(items: List<String>) = items.filter { it.isNotEmpty() }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("ViewProviderDerivation")
    }

    fun `test does not flag derivation outside a ViewProvider file`() {
        lint()
            .files(
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/foo/FooViewModel.kt",
                    """
                    package com.hluhovskyi.zero.foo
                    data class State(val items: List<String>)
                    fun derive(state: State) = state.items.any { it.isNotEmpty() }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test allowlists EnumEntries from kotlin enum entries`() {
        lint()
            .files(
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/foo/FooViewProvider.kt",
                    """
                    package com.hluhovskyi.zero.foo
                    enum class Tab { A, B, C }
                    fun render() = Tab.entries.associateWith { it.name }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test does not flag size or simple field access on state`() {
        lint()
            .files(
                kotlin(
                    "src/main/java/com/hluhovskyi/zero/foo/FooViewProvider.kt",
                    """
                    package com.hluhovskyi.zero.foo
                    data class State(val items: List<String>)
                    fun render(state: State): Int = state.items.size
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
