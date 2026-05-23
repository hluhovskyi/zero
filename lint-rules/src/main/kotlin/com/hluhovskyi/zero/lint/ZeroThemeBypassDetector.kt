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
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.USimpleNameReferenceExpression

class ZeroThemeBypassDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java, USimpleNameReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            if (isAllowlisted(context)) return
            val resolved = node.resolve() ?: return
            if (!resolved.isComposeColorBuilder()) return
            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
        }

        override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
            if (isAllowlisted(context)) return
            if (node.identifier !in BANNED_NAMES) return
            val resolved = node.resolve() as? PsiMember ?: return
            if (!resolved.isOnComposeColor()) return
            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
        }
    }

    private fun isAllowlisted(context: JavaContext): Boolean {
        val pkg = context.uastFile?.packageName.orEmpty()
        if (pkg.startsWith(THEME_PACKAGE)) return true
        val key = "$pkg.${context.file.nameWithoutExtension}"
        return key in ALLOWLISTED_FILE_FQNS
    }

    private fun PsiMethod.isComposeColorBuilder(): Boolean {
        if (name != "Color") return false
        val containingClass = containingClass ?: return false
        return containingClass.qualifiedName == COMPOSE_COLOR_KT_FQN
    }

    /**
     * `Color.White` etc. can resolve to a [PsiField] on `Color.Companion`, a synthetic getter
     * `PsiMethod` on the companion, or — under `@JvmField` — a field on `Color` itself. Accept
     * any member whose containing class chain leads back to `androidx.compose.ui.graphics.Color`.
     */
    private fun PsiMember.isOnComposeColor(): Boolean {
        var cls: PsiClass? = containingClass ?: return false
        while (cls != null) {
            if (cls.qualifiedName == COMPOSE_COLOR_FQN) return true
            cls = cls.containingClass
        }
        return false
    }

    companion object {
        private const val COMPOSE_COLOR_FQN = "androidx.compose.ui.graphics.Color"
        private const val COMPOSE_COLOR_KT_FQN = "androidx.compose.ui.graphics.ColorKt"
        private const val THEME_PACKAGE = "com.hluhovskyi.zero.ui.theme"

        // Package-qualified to avoid allowlisting a stray Colors.kt or UiColorScheme.kt elsewhere.
        // Both files are entity-palette plumbing (orthogonal to ZeroTheme app colors).
        private val ALLOWLISTED_FILE_FQNS = setOf(
            "com.hluhovskyi.zero.ui.UiColorScheme",
            "com.hluhovskyi.zero.ui.common.Colors",
        )

        private val BANNED_NAMES = setOf(
            "White",
            "Black",
            "Red",
            "Green",
            "Blue",
            "Gray",
            "Yellow",
            "Magenta",
            "Cyan",
        )

        private const val MESSAGE =
            "Use ZeroTheme.colors.<token> instead of constructing or referencing Color directly. " +
                "See docs/agents/color-scheme.md."

        val ISSUE: Issue = Issue.create(
            id = "ZeroThemeBypass",
            briefDescription = "Color bypass — route through ZeroTheme.colors",
            explanation =
            "App colors must come from `ZeroTheme.colors.<token>` so a future dark palette " +
                "swaps in without touching callers. Direct `Color(0x…)` literals and named " +
                "`Color.White`/`Color.Black`/... references bypass that and won't switch. " +
                "Suppress per-site with `@Suppress(\"ZeroThemeBypass\")` for intentional " +
                "bespoke palettes (e.g. a widget's designed sub-palette).",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ZeroThemeBypassDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
