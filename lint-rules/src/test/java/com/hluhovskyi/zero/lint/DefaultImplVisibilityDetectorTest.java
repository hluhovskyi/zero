package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class DefaultImplVisibilityDetectorTest extends LintDetectorTest {

    @Override
    protected Detector getDetector() {
        return new DefaultImplVisibilityDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(DefaultImplVisibilityDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsWhenDefaultClassIsNotInternal() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "class DefaultTransactionViewModel\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testCleanWhenDefaultClassIsInternal() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "internal class DefaultTransactionViewModel\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testIgnoresNestedDefaultClass() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "sealed interface Mode {\n" +
                    "    data class Default(val x: Int) : Mode\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
