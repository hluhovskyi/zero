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
    )

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(vendorName = "Zero")
}
