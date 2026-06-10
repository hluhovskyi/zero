package com.hluhovskyi.zero.lint

import com.android.tools.lint.client.api.LintClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the agent docs to reality so they can't silently rot:
 * - every registered lint issue ID must be documented in docs/agents/architecture.md;
 * - every markdown file under docs/agents must be reachable from the root AGENTS.md index;
 * - relative markdown links in agent docs must resolve.
 *
 * Runs as a plain unit test so the main CI `test` step gates it; a dedicated CI job runs it
 * for docs-only PRs (which skip gradle otherwise).
 */
class DocsConsistencyTest {

    init {
        // Issue.create() refuses to run outside a lint client context.
        LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
    }

    private val repoRoot: File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun `every lint issue id is documented in architecture md`() {
        val doc = File(repoRoot, "docs/agents/architecture.md").readText()
        val missing = ZeroIssueRegistry().issues.map { it.id }.filterNot { it in doc }
        assertTrue(
            "Lint issue(s) missing from docs/agents/architecture.md § Lint Enforcement: $missing. " +
                "Document every registered rule there (by issue ID) in the same change that adds it.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every docs-agents file is linked from root AGENTS md`() {
        val index = File(repoRoot, "AGENTS.md").readText()
        val missing = File(repoRoot, "docs/agents").listFiles { f -> f.extension == "md" }
            .orEmpty()
            .map { it.name }
            .filterNot { it in index }
        assertTrue(
            "docs/agents file(s) not referenced from AGENTS.md: $missing. " +
                "Link them in the Reference Docs section, or fold them into an existing doc.",
            missing.isEmpty(),
        )
    }

    @Test
    fun `relative markdown links in agent docs resolve`() {
        val linkPattern = Regex("\\]\\(([^)\\s]+)\\)")
        val broken = agentDocFiles().flatMap { file ->
            linkPattern.findAll(file.readText())
                .map { it.groupValues[1] }
                .filterNot { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("mailto:") }
                .map { it.substringBefore('#') }
                .filter { it.isNotEmpty() }
                .filterNot { File(file.parentFile, it).exists() }
                .map { "${file.relativeTo(repoRoot)} → $it" }
                .toList()
        }
        assertTrue("Broken relative markdown link(s): $broken", broken.isEmpty())
    }

    /** All docs/agents files plus every AGENTS.md in the repo (build and tool dirs excluded). */
    private fun agentDocFiles(): List<File> {
        val docs = File(repoRoot, "docs/agents").listFiles { f -> f.extension == "md" }.orEmpty().toList()
        val skipped = setOf("build", ".git", ".gradle", ".gradle-home", ".claude", ".worktrees", "node_modules")
        val agentsFiles = repoRoot.walkTopDown()
            .onEnter { it.name !in skipped }
            .filter { it.isFile && it.name == "AGENTS.md" }
            .toList()
        return docs + agentsFiles
    }
}
