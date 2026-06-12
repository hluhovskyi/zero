package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UImportStatement

/**
 * Bans Material 2 imports (`androidx.compose.material.*`) everywhere except the icon packs and
 * the bottom-sheet navigator island (the only M2 left after the Material 3 migration, PR #295).
 * See zero-ui/AGENTS.md and docs/agents/color-scheme.md § The Material 2 island.
 */
class MaterialTwoImportDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitFile(node: UFile) {
            val path = context.file.path.replace('\\', '/')
            if (ISLAND_FILES.any { path.endsWith(it) }) return
            for (import in node.imports) {
                checkImport(context, import)
            }
        }
    }

    private fun checkImport(context: JavaContext, node: UImportStatement) {
        val fqn = node.importReference?.asSourceString()
            ?: (node.sourcePsi as? KtImportDirective)?.importedFqName?.asString()
            ?: return
        if (!fqn.startsWith(M2_PREFIX)) return
        if (ALLOWED_PREFIXES.any { fqn.startsWith(it) }) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Material 2 import `$fqn` — use `androidx.compose.material3` instead. M2 is " +
                "allowed only in the bottom-sheet navigator island (see " +
                "docs/agents/color-scheme.md).",
        )
    }

    companion object {
        private const val M2_PREFIX = "androidx.compose.material."

        /** Icon packs ship under `material.icons` with no M3 equivalent. */
        private val ALLOWED_PREFIXES = listOf(
            "androidx.compose.material.icons.",
        )

        /**
         * The bottom-sheet navigator island — `material-navigation` has no M3 equivalent, so
         * these files keep M2 (with every color pinned). Moving the island means updating this
         * list in the same change.
         */
        private val ISLAND_FILES = listOf(
            "/activity/MainActivityViewProvider.kt",
            "/activity/screens/MainActivityScreenComponent.kt",
            "/activity/screens/MainActivityScreenViewProvider.kt",
        )

        val ISSUE: Issue = Issue.create(
            id = "MaterialTwoImport",
            briefDescription = "Use Material 3 — androidx.compose.material is only allowed in the bottom-sheet island",
            explanation = "The app migrated to Material 3 (PR #295). New `androidx.compose.material` " +
                "imports compile but force a hand-migration (including signature changes) the " +
                "moment they reach a migrated module. The only sanctioned M2 is the " +
                "bottom-sheet navigator island in the app module and the " +
                "`androidx.compose.material.icons` packs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MaterialTwoImportDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
