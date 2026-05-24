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
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprDown

/**
 * Flags collection-derivation calls (`.filter`, `.any`, `.sortedBy`, `.sumOf`, …) inside any
 * `*ViewProvider.kt` whose receiver chain starts at a variable named `state`.
 *
 * The ViewProvider is a pure renderer — domain derivation belongs in the UseCase, view-shape
 * adaptation belongs in the ViewModel (sealed `Item` per visual variant). See
 * `docs/agents/architecture.md` § "ViewModel UI Shape" and `zero-core/AGENTS.md` rule 7.
 */
class StateCollectionDerivationDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = DERIVATION_OPERATORS.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.file.nameWithoutExtension.endsWith("ViewProvider")) return
        val receiver = node.receiver?.skipParenthesizedExprDown() ?: return
        if (!receiverChainStartsAtState(receiver)) return
        context.report(ISSUE, node, context.getLocation(node), MESSAGE)
    }

    /**
     * True when `expr` is `state.<property>` (or a longer chain) — i.e. the root of the
     * qualifier chain is a simple reference named `state`.
     */
    private fun receiverChainStartsAtState(expr: UExpression): Boolean {
        var current: UExpression? = expr
        while (current is UQualifiedReferenceExpression) {
            current = current.receiver.skipParenthesizedExprDown()
        }
        val root = current as? USimpleNameReferenceExpression ?: return false
        return root.identifier == "state"
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

        private const val MESSAGE =
            "Derivation on `state.*` belongs in the ViewModel, not the ViewProvider. " +
                "Expose a pre-computed field on the State (sealed `Item` for variant rows, " +
                "boolean/Int for predicates and counts). See docs/agents/architecture.md."

        val ISSUE: Issue = Issue.create(
            id = "ViewProviderStateDerivation",
            briefDescription = "ViewProvider must not derive over state collections",
            explanation = "The ViewProvider is a pure renderer. Sorting, filtering, " +
                "predicate checks, and aggregations over `state.*` collections belong in the " +
                "ViewModel (where the canonical pattern is to expose a sealed `Item` per " +
                "visual variant). Reference: TransactionViewModel.Item; rule in " +
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
