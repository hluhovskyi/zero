package com.hluhovskyi.zero.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class SealedSubtypeDuplicatePropertyDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = SealedSubtypeDuplicatePropertyDetector()
    override fun getIssues(): List<Issue> = listOf(SealedSubtypeDuplicatePropertyDetector.ISSUE)

    fun `test flags property duplicated on all subtypes`() {
        lint()
            .files(
                kotlin(
                    """
                    sealed interface Transaction {
                        data class Expense(val notes: String? = null) : Transaction
                        data class Income(val notes: String? = null) : Transaction
                        data class Transfer(val notes: String? = null) : Transaction
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectContains("SealedSubtypeDuplicateProperty")
    }

    fun `test clean when property only on some subtypes`() {
        lint()
            .files(
                kotlin(
                    """
                    sealed interface Transaction {
                        data class Expense(val categoryId: String) : Transaction
                        data class Income(val categoryId: String) : Transaction
                        data class Transfer(val targetAccount: String) : Transaction
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when property is overriding base interface`() {
        lint()
            .files(
                kotlin(
                    """
                    sealed interface Transaction {
                        val notes: String?
                        data class Expense(override val notes: String? = null) : Transaction
                        data class Income(override val notes: String? = null) : Transaction
                        data class Transfer(override val notes: String? = null) : Transaction
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when property types differ across subtypes`() {
        lint()
            .files(
                kotlin(
                    """
                    sealed interface Foo {
                        data class Bar(val x: Int) : Foo
                        data class Baz(val x: Long) : Foo
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }

    fun `test clean when one subtype is an object with no properties`() {
        lint()
            .files(
                kotlin(
                    """
                    sealed interface State {
                        object Loading : State
                        data class Success(val notes: String?) : State
                        data class Error(val notes: String?) : State
                    }
                    """,
                ).indented(),
            )
            .run()
            .expectClean()
    }
}
