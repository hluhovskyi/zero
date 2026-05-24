package com.hluhovskyi.zero.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Flags collection-derivation calls (`.filter`, `.any`, `.sortedBy`, `.sumOf`, …) inside any
 * `*ViewProvider.kt`, regardless of receiver. Derivation belongs in the ViewModel — the
 * composable should iterate pre-shaped data.
 *
 * Exempted via a type-substring [ALLOWLISTED_TYPE_FRAGMENTS] check on the receiver:
 *   - `androidx.compose.*` — Compose internals like `LazyListLayoutInfo.visibleItemsInfo` are a
 *     legitimate view-layer concern (scroll-driven pagination, layout introspection).
 *   - `kotlin.enums.EnumEntries` — `MyEnum.entries.associateWith { ... }` is a static
 *     enum-to-display-name table, not domain derivation.
 *
 * See `docs/agents/architecture.md` § "ViewModel UI Shape" and `zero-core/AGENTS.md` rule 7.
 */
class StateCollectionDerivationDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = DERIVATION_OPERATORS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.file.nameWithoutExtension.endsWith("ViewProvider")) return
        val receiver = node.receiver ?: return
        val typeText = receiver.getExpressionType()?.canonicalText.orEmpty()
        if (ALLOWLISTED_TYPE_FRAGMENTS.any { it in typeText }) return
        context.report(ISSUE, node, context.getLocation(node), MESSAGE)
    }

    companion object {
        private val DERIVATION_OPERATORS = setOf(
            "filter",
            "filterNot",
            "any",
            "none",
            "all",
            "firstOrNull",
            "lastOrNull",
            "find",
            "count",
            "sorted",
            "sortedBy",
            "sortedByDescending",
            "groupBy",
            "associateBy",
            "associateWith",
            "indexOfFirst",
            "partition",
            "flatMap",
            "distinctBy",
            "minByOrNull",
            "maxByOrNull",
            "sumOf",
            "fold",
            "reduce",
        )

        /**
         * Substring fragments matched against the receiver's canonical type text (which
         * includes type arguments, so `List<androidx.compose.X>` is caught by the
         * `androidx.compose.` fragment).
         */
        private val ALLOWLISTED_TYPE_FRAGMENTS = listOf(
            "androidx.compose.",
            "kotlin.enums.",
        )

        private const val MESSAGE =
            "Derivation belongs in the ViewModel, not the ViewProvider. Expose a pre-computed " +
                "field on the State (sealed `Item` for variant rows, boolean/Int for predicates " +
                "and counts). See docs/agents/architecture.md."

        val ISSUE: Issue = Issue.create(
            id = "ViewProviderDerivation",
            briefDescription = "ViewProvider must not run collection derivation",
            explanation = "The ViewProvider is a pure renderer. Sorting, filtering, predicate " +
                "checks, and aggregations belong in the ViewModel (where the canonical pattern " +
                "is to expose a sealed `Item` per visual variant). Exempt: Compose-internal " +
                "receivers (LazyListLayoutInfo etc.) and enum `entries`. " +
                "Reference: TransactionViewModel.Item; rule in " +
                "docs/agents/architecture.md § ViewModel UI Shape and zero-core/AGENTS.md rule 7.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                StateCollectionDerivationDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
