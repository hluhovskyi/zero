package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class ZeroIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(
        DefaultImplVisibilityDetector.ISSUE,
        ViewProviderVisibilityDetector.ISSUE,
        ViewProviderDependencyDetector.ISSUE,
        StateCollectionDerivationDetector.ISSUE,
        HandlerFunInterfaceDetector.ISSUE,
        DatabaseComponentEncapsulationDetector.ISSUE,
        RemoteComponentEncapsulationDetector.ISSUE,
        KmpReadinessDetector.ISSUE,
        TestBridgeBoundaryDetector.ISSUE,
        TestBridgeProductionPurityDetector.ISSUE,
        SyncEntitySerialNameDetector.ISSUE,
        NoNamedAnnotationDetector.ISSUE,
        UnhandledCloseableDetector.ISSUE,
        UnhandledJobDetector.ISSUE,
        FullyQualifiedReferenceDetector.ISSUE,
        SealedSubtypeDuplicatePropertyDetector.ISSUE,
        HardcodedComposableStringDetector.ISSUE,
        UppercaseStringResourceDetector.ISSUE,
        BreadcrumbsLiteralOnlyDetector.ISSUE,
        ScopedComponentBuilderDetector.ISSUE,
        ZeroThemeBypassDetector.ISSUE,
        DirectClockUsageDetector.ISSUE,
        DispatchersDefaultDetector.ISSUE,
        MaterialTwoImportDetector.ISSUE,
        NoImplSuffixDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(vendorName = "Zero")
}
