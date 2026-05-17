package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

/**
 * Flags `*UseCase` interfaces that declare a single abstract member — the canonical "thin wrapper
 * around a repository call" smell. A use-case earns its keep when it has 2+ callers, crosses a
 * Dagger scope, hosts non-trivial business logic, or is the documented public contract of a
 * feature module. A one-method interface with one implementation that lives in the same feature
 * graph is almost always speculative — inline it into the VM (or repository) and revive the
 * interface when a second caller or implementation actually appears.
 *
 * Suppress with `@Suppress("SpeculativeUseCase")` at the interface declaration when the abstraction
 * is intentional (e.g. an explicit DI seam, an expected second implementation, a contract that
 * external modules consume).
 */
class SpeculativeUseCaseDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val name = node.name ?: return
            if (!name.endsWith("UseCase")) return
            if (!node.isInterface) return
            // Skip generic action-style use cases that follow the ActionStateModel pattern; those
            // intentionally expose a single `perform` method backed by a sealed Action hierarchy.
            if (extendsActionStateModel(node)) return

            val qualifiedName = node.qualifiedName
            val abstractMembers = node.methods
                .filter { method ->
                    // Restrict to members declared on this interface (skip inherited).
                    method.containingClass?.qualifiedName == qualifiedName
                }
                .filter { method -> method.uastBody == null }
                .filter { it.name != "<init>" }
            if (abstractMembers.size != 1) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node as UElement),
                MESSAGE,
            )
        }
    }

    private fun extendsActionStateModel(node: UClass): Boolean {
        val supers = node.superTypes.mapNotNull { it.canonicalText }
        return supers.any { it.contains("ActionStateModel") }
    }

    companion object {
        private const val MESSAGE =
            "Single-method `*UseCase` interface looks speculative. Inline the implementation " +
                "into the VM (or call the repository directly) unless this UseCase has 2+ callers, " +
                "crosses a Dagger scope, hosts non-trivial logic, or is a documented module contract. " +
                "See docs/agents/architecture.md."

        val ISSUE: Issue = Issue.create(
            id = "SpeculativeUseCase",
            briefDescription = "Single-method UseCase interface may be speculative",
            explanation =
            "A `*UseCase` interface with exactly one abstract method and one implementation in " +
                "the same feature graph is almost always premature abstraction. The interface earns " +
                "no testability (one impl), no swapability (no second producer), and no contract " +
                "documentation (one caller). Inline the call into the VM or repository, and revive " +
                "the interface when a real second caller or implementation appears.\n\n" +
                "Suppress with `@Suppress(\"SpeculativeUseCase\")` on the interface when the " +
                "abstraction is intentional (e.g. external module contract, expected second impl).",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SpeculativeUseCaseDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
