package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class ViewProviderDependencyDetectorTest extends LintDetectorTest {

    @Override
    protected Detector getDetector() {
        return new ViewProviderDependencyDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(ViewProviderDependencyDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsWhenViewProviderInjectsARepository() {
        lint()
            .testModes(com.android.tools.lint.checks.infrastructure.TestMode.DEFAULT)
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "class TransactionRepository\n" +
                    "class TransactionViewModel\n" +
                    "\n" +
                    "internal class TransactionViewProvider(\n" +
                    "    private val viewModel: TransactionViewModel,\n" +
                    "    private val repo: TransactionRepository,\n" +
                    ")\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testReportsWhenViewProviderInjectsAUseCase() {
        lint()
            .testModes(com.android.tools.lint.checks.infrastructure.TestMode.DEFAULT)
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "class TransactionUseCase\n" +
                    "class TransactionViewModel\n" +
                    "\n" +
                    "internal class TransactionViewProvider(\n" +
                    "    private val viewModel: TransactionViewModel,\n" +
                    "    private val useCase: TransactionUseCase,\n" +
                    ")\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testCleanWhenViewProviderOnlyInjectsViewModelAndOtherSafeTypes() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "class ImageLoader\n" +
                    "class TransactionViewModel\n" +
                    "\n" +
                    "internal class TransactionViewProvider(\n" +
                    "    private val viewModel: TransactionViewModel,\n" +
                    "    private val imageLoader: ImageLoader,\n" +
                    ")\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testIgnoresNonViewProviderClasses() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.transactions\n" +
                    "\n" +
                    "class TransactionRepository\n" +
                    "\n" +
                    "class TransactionHelper(\n" +
                    "    private val repo: TransactionRepository,\n" +
                    ")\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
