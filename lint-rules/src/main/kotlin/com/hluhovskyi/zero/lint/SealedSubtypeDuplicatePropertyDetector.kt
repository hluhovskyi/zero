package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UField

class SealedSubtypeDuplicatePropertyDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val sourcePsi = node.sourcePsi as? KtModifierListOwner ?: return
            if (!sourcePsi.hasModifier(KtTokens.SEALED_KEYWORD)) return

            val subtypes = node.innerClasses.toList()
            if (subtypes.size < 2) return

            // Collect non-override, non-static fields per subtype
            val subtypeProps: List<Map<String, UField>> = subtypes.map { subtype ->
                subtype.fields
                    .filterIsInstance<UField>()
                    .filter { field ->
                        if (field.isStatic) return@filter false
                        val psi = field.sourcePsi as? KtModifierListOwner ?: return@filter false
                        !psi.hasModifier(KtTokens.OVERRIDE_KEYWORD)
                    }
                    .mapNotNull { field -> field.name?.let { name -> name to field } }
                    .toMap()
            }

            if (subtypeProps.any { it.isEmpty() }) return

            val commonNames = subtypeProps
                .map { it.keys }
                .reduce { acc, keys -> acc intersect keys }

            for (name in commonNames) {
                val fields = subtypeProps.map { it.getValue(name) }
                val types = fields.map { it.type.canonicalText }.toSet()
                if (types.size != 1) continue

                fields.forEach { field ->
                    context.report(
                        ISSUE,
                        field,
                        context.getLocation(field as org.jetbrains.uast.UElement),
                        "`$name` is declared on every subtype of sealed `${node.name}` — " +
                            "move it to the sealed interface.",
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "SealedSubtypeDuplicateProperty",
            briefDescription = "Property duplicated across all sealed subtypes",
            explanation = "A property with the same name and type on every subtype of a sealed " +
                "interface belongs on the sealed interface itself. Duplicating it forces callers " +
                "to use a `when` expression to access it.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SealedSubtypeDuplicatePropertyDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
