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
 * Keeps modules listed in [KMP_READY_MODULE_PATHS] free of JVM/Android-only symbols so they
 * stay portable to Kotlin Multiplatform. Add a new module by appending its path fragment to the
 * list — no per-module detector needed.
 */
class KmpReadinessDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitFile(node: UFile) {
            val ourPath = context.file.path.replace('\\', '/')
            val module = KMP_READY_MODULE_PATHS.firstOrNull { it in ourPath } ?: return
            for (import in node.imports) {
                checkImport(context, import, module)
            }
        }
    }

    private fun checkImport(context: JavaContext, node: UImportStatement, module: String) {
        val fqn = node.importReference?.asSourceString()
            ?: (node.sourcePsi as? KtImportDirective)?.importedFqName?.asString()
            ?: return
        val matched = FORBIDDEN_PREFIXES.firstOrNull { fqn.startsWith(it) } ?: return

        val moduleName = module.trim('/')
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "$moduleName is KMP-ready — Android, OkHttp, coroutines-android, and java.time " +
                "symbols would break the portability guarantee. Move the implementation to an " +
                "Android-bound module (e.g. :app, :zero-remote) and expose only a pure-Kotlin " +
                "interface here (kotlinx.datetime for time). " +
                "Forbidden import: $fqn (matched prefix $matched).",
        )
    }

    companion object {
        /**
         * Path fragments (with leading and trailing `/`) of modules that must stay KMP-ready.
         * Add a new module here as soon as it should be portability-locked.
         */
        private val KMP_READY_MODULE_PATHS = listOf(
            "/zero-api/",
            "/zero-sync/",
            "/zero-backup/",
        )

        private val FORBIDDEN_PREFIXES = listOf(
            "okhttp3.",
            "android.",
            "androidx.",
            "kotlinx.coroutines.android.",
            "java.time.",
        )

        val ISSUE: Issue = Issue.create(
            id = "KmpReadiness",
            briefDescription = "KMP-ready module must not import JVM/Android-only symbols",
            explanation = "Modules listed in KmpReadinessDetector.KMP_READY_MODULE_PATHS are " +
                "intentionally pure-Kotlin so they can be migrated to Kotlin Multiplatform later. " +
                "Android, OkHttp, coroutines-android, and java.time imports break that guarantee " +
                "(time goes through kotlinx.datetime). " +
                "Implementations of HTTP transport, OAuth, secure storage, and other platform " +
                "concerns belong in Android-bound modules (:app, :zero-remote, :zero-auth, …).",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                KmpReadinessDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
