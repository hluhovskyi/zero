package com.hluhovskyi.zero.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UppercaseStringResourceDetector : ResourceXmlDetector() {

    override fun getApplicableElements() = listOf("string")

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent ?: return
        if (!isAllUppercase(text)) return
        context.report(ISSUE, element, context.getLocation(element), MESSAGE)
    }

    private fun isAllUppercase(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        return letters.length >= 2 && letters.all { it.isUpperCase() }
    }

    companion object {
        private const val MESSAGE =
            "String resource contains all-caps text — store in natural case and call `.uppercase()` at the view layer."

        val ISSUE: Issue = Issue.create(
            id = "UppercaseStringResource",
            briefDescription = "All-caps text in string resource",
            explanation =
            "String resources should store text in natural case. Apply `.uppercase()` in the " +
                "Composable so the string remains reusable in other contexts without forcing " +
                "uppercase presentation.",
            category = Category.I18N,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                UppercaseStringResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
            ),
        )
    }
}
