package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class NoNamedAnnotationDetectorTest extends LintDetectorTest {

    @Override
    protected Detector getDetector() {
        return new NoNamedAnnotationDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(NoNamedAnnotationDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsNamedOnParameter() {
        lint()
            .files(
                kotlin(
                    "package javax.inject\n" +
                    "annotation class Named(val value: String = \"\")\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "\n" +
                    "import javax.inject.Named\n" +
                    "\n" +
                    "class MyClass(@Named(\"someKey\") val id: String)\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testReportsNamedOnBindsInstanceMethod() {
        lint()
            .files(
                kotlin(
                    "package javax.inject\n" +
                    "annotation class Named(val value: String = \"\")\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "\n" +
                    "import javax.inject.Named\n" +
                    "\n" +
                    "interface Builder {\n" +
                    "    fun id(@Named(\"id\") id: String): Builder\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1);
    }

    @Test
    public void testCleanWhenUsingCustomQualifier() {
        lint()
            .files(
                kotlin(
                    "package javax.inject\n" +
                    "annotation class Qualifier\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "\n" +
                    "import javax.inject.Qualifier\n" +
                    "\n" +
                    "@Qualifier\n" +
                    "@Retention(AnnotationRetention.BINARY)\n" +
                    "annotation class MyId\n" +
                    "\n" +
                    "class MyClass(@MyId val id: String)\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
