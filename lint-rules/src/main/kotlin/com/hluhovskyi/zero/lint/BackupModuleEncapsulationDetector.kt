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

class BackupModuleEncapsulationDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitFile(node: UFile) {
            val ourPath = context.file.path.replace('\\', '/')
            if ("/zero-backup/" !in ourPath) return
            for (import in node.imports) {
                checkImport(context, import)
            }
        }
    }

    private fun checkImport(context: JavaContext, node: UImportStatement) {
        val fqn = node.importReference?.asSourceString()
            ?: (node.sourcePsi as? KtImportDirective)?.importedFqName?.asString()
            ?: return
        val matched = FORBIDDEN_PREFIXES.firstOrNull { fqn.startsWith(it) } ?: return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "zero-backup is pure Kotlin JVM — Android, OkHttp, and coroutines-android symbols " +
                "belong in app/ or zero-remote/, not here. Forbidden import: $fqn " +
                "(matched prefix $matched). See zero-backup/AGENTS.md.",
        )
    }

    companion object {
        private val FORBIDDEN_PREFIXES = listOf(
            "okhttp3.",
            "android.",
            "androidx.",
            "kotlinx.coroutines.android.",
        )

        val ISSUE: Issue = Issue.create(
            id = "BackupModuleEncapsulation",
            briefDescription = "zero-backup must remain a pure Kotlin JVM module",
            explanation = "The :zero-backup module orchestrates backup as pure Kotlin so it can be " +
                "tested without Android. Importing Android, OkHttp, or coroutines-android symbols " +
                "breaks that guarantee. Implementations of HTTP transport, OAuth, and secure storage " +
                "belong in :zero-remote, :zero-auth, or :app. See zero-backup/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                BackupModuleEncapsulationDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
