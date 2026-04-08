package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class HandlerFunInterfaceDetectorTest extends LintDetectorTest {

    @Override
    protected Detector getDetector() {
        return new HandlerFunInterfaceDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(HandlerFunInterfaceDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsWhenOnHandlerIsAPlainInterface() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "interface OnTransactionSavedHandler {\n" +
                    "    fun onSaved()\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testCleanWhenOnHandlerIsAFunInterface() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "fun interface OnTransactionSavedHandler {\n" +
                    "    fun onSaved()\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testIgnoresInterfacesNotMatchingOnHandlerPattern() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "interface TransactionListener {\n" +
                    "    fun onEvent()\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
