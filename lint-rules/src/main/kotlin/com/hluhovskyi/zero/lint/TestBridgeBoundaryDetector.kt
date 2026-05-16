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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UImportStatement
import java.util.EnumSet

class TestBridgeBoundaryDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UFile::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitFile(node: UFile) {
            val ourPath = context.file.path.replace('\\', '/')
            if ("/src/androidTest/" !in ourPath) return
            for (import in node.imports) {
                checkImport(context, import)
            }
        }
    }

    private fun checkImport(context: JavaContext, node: UImportStatement) {
        val resolved = node.resolve() as? PsiClass ?: return
        val containingPath = resolved.containingFile
            ?.virtualFile?.path?.replace('\\', '/') ?: return

        if ("/src/main/" !in containingPath) return

        val fqn = resolved.qualifiedName ?: return
        if (isAllowed(fqn)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Tests must go through zero-test-bridge — add a method to DatabaseTestBridge " +
                "(or a new bridge interface) instead of importing $fqn directly.",
        )
    }

    private fun isAllowed(fqn: String): Boolean =
        fqn.startsWith("com.hluhovskyi.zero.testbridge.") ||
            fqn == "com.hluhovskyi.zero.activity.MainActivity"

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "TestBridgeBoundary",
            briefDescription = "androidTest may not import production internals",
            explanation = "Code under app/src/androidTest/** must only reach into production through " +
                "zero-test-bridge (com.hluhovskyi.zero.testbridge.*) or the activity under test " +
                "(MainActivity). If a test needs new production state, grow the DatabaseTestBridge " +
                "interface (or add a new bridge) rather than importing repositories, components, or " +
                "other prod internals directly. See zero-test-bridge/AGENTS.md.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                TestBridgeBoundaryDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}
