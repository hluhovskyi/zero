package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class ViewProviderVisibilityDetectorTest extends LintDetectorTest {

    @Override
    protected Detector getDetector() {
        return new ViewProviderVisibilityDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(ViewProviderVisibilityDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsWhenViewProviderIsNotInternal() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "class TransactionViewProvider\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testCleanWhenViewProviderIsInternal() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "internal class TransactionViewProvider\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testIgnoresBaseViewProviderInterface() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.common\n" +
                    "\n" +
                    "interface ViewProvider\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
