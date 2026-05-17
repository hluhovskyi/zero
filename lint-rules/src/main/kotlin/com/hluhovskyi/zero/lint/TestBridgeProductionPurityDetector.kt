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

class TestBridgeProductionPurityDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitFile(node: UFile) {
            val ourPath = context.file.path.replace('\\', '/')
            if ("/zero-test-bridge/src/main/" !in ourPath) return
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
            "zero-test-bridge ships in the production APK — test framework code belongs " +
                "in app/androidTest, not here. Forbidden import: $fqn (matched prefix $matched).",
        )
    }

    companion object {
        private val FORBIDDEN_PREFIXES = listOf(
            "org.junit.",
            "androidx.test.",
            "androidx.compose.ui.test.",
            "kotlin.test.",
            "org.mockito.",
            "io.mockk.",
        )

        val ISSUE: Issue = Issue.create(
            id = "TestBridgeProductionPurity",
            briefDescription = "zero-test-bridge must not import test framework code",
            explanation = "The :zero-test-bridge module is a regular implementation dependency of " +
                ":app and ships into the production APK. Test-framework symbols (JUnit, AndroidX-test, " +
                "Compose-test, kotlin.test, Mockito, MockK) belong in app/androidTest, not here. " +
                "See zero-test-bridge/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                TestBridgeProductionPurityDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
