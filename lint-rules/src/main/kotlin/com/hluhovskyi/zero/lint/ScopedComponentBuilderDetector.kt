package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UMethod

/**
 * Errors on `@Provides @<Scope> fun foo(...): SomeComponent.Builder`.
 *
 * Scoping a Builder provider makes it a shared mutable singleton across the scope's lifetime.
 * Consumers that call different `@BindsInstance` setters on it pollute each other — state from one
 * navigation entry leaks into the next. The fix is to leave the provider unscoped so each consumer
 * gets a fresh Builder, with factory defaults intact for fields the consumer doesn't touch.
 */
class ScopedComponentBuilderDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitMethod(node: UMethod) {
            if (!node.hasAnnotation("dagger.Provides")) return

            val scopeAnnotation = node.uAnnotations.firstOrNull { ann ->
                val fqn = ann.qualifiedName ?: return@firstOrNull false
                if (fqn == DAGGER_PROVIDES) return@firstOrNull false
                val cls = ann.resolve() ?: return@firstOrNull false
                cls.hasAnnotation(JAVAX_SCOPE)
            } ?: return

            if (!returnsComponentBuilder(node)) return

            context.report(
                ISSUE,
                node,
                context.getLocation(scopeAnnotation),
                MESSAGE,
            )
        }
    }

    private fun returnsComponentBuilder(method: UMethod): Boolean {
        val returnType = method.returnType as? PsiClassType ?: return false
        val resolved: PsiClass = returnType.resolve() ?: return false
        // Inner Builder of a @dagger.Component is the canonical case: container has @dagger.Component
        // and the Builder itself has @dagger.Component.Builder.
        if (resolved.hasAnnotation(DAGGER_COMPONENT_BUILDER)) return true
        val container = resolved.containingClass ?: return false
        if (resolved.name != "Builder") return false
        return container.hasAnnotation(DAGGER_COMPONENT)
    }

    companion object {
        private const val DAGGER_PROVIDES = "dagger.Provides"
        private const val DAGGER_COMPONENT = "dagger.Component"
        private const val DAGGER_COMPONENT_BUILDER = "dagger.Component.Builder"
        private const val JAVAX_SCOPE = "javax.inject.Scope"

        private const val MESSAGE =
            "Component Builder providers must be unscoped. Scoping makes the Builder a shared " +
                "mutable singleton; @BindsInstance values from one consumer leak into the next. " +
                "See docs/agents/dependency-injection.md."

        val ISSUE: Issue = Issue.create(
            id = "ScopedComponentBuilder",
            briefDescription = "Component Builder providers must be unscoped",
            explanation =
            "A scoped `@Provides` for a Dagger `Component.Builder` returns the same Builder " +
                "instance to every consumer for the lifetime of the scope. Different consumers " +
                "call different `@BindsInstance` setters on it — values from a previous consumer " +
                "persist for the next one, producing subtle cross-screen state leaks. Leave the " +
                "Builder provider unscoped so each consumer gets a fresh Builder with the factory " +
                "defaults intact.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ScopedComponentBuilderDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
