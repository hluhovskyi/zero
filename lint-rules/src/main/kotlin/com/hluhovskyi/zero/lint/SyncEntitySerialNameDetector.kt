package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField

class SyncEntitySerialNameDetector :
    Detector(),
    Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            // Only check @Serializable classes in the sync package
            val packageName = node.qualifiedName ?: return
            if (!packageName.contains(".sync.")) return
            if (node.findAnnotation("kotlinx.serialization.Serializable") == null) return

            for (field in node.fields) {
                if (!field.hasSerialName()) {
                    context.report(
                        ISSUE,
                        field,
                        context.getLocation(field as UElement),
                        "Field `${field.name}` in @Serializable sync class must have @SerialName. " +
                            "Omitting it ties the JSON key to the Kotlin property name, " +
                            "which breaks existing backups if the property is renamed.",
                    )
                }
            }
        }
    }

    private fun UField.hasSerialName(): Boolean {
        if (findAnnotation("kotlinx.serialization.SerialName") != null) return true
        // Annotations on Kotlin constructor properties land on the parameter, not the field —
        // the standalone JVM lint task doesn't surface them on the UField, so check the PSI.
        val psi = sourcePsi as? KtAnnotated ?: return false
        return psi.annotationEntries.any { it.shortName?.asString() == "SerialName" }
    }

    companion object {
        val ISSUE: Issue = Issue.create(
            id = "SyncEntityFieldMustHaveSerialName",
            briefDescription = "@Serializable sync fields must have @SerialName",
            explanation = "Every field on a @Serializable class in the sync package must carry " +
                "@SerialName to decouple the JSON key from the Kotlin property name. " +
                "This prevents silent format breakage when properties are renamed.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                SyncEntitySerialNameDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
