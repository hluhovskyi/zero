package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class RemoteComponentEncapsulationDetectorTest extends LintDetectorTest {

    private static final TestFile OKHTTP_STUB = java(
        "package okhttp3;\n" +
        "public class OkHttpClient {}\n"
    );

    private static final TestFile PLAY_INTEGRITY_STUB = java(
        "package com.google.android.play.core.integrity;\n" +
        "public class StandardIntegrityManager {}\n"
    );

    private static final TestFile JSON_STUB = java(
        "package kotlinx.serialization.json;\n" +
        "public class Json {}\n"
    );

    @Override
    protected Detector getDetector() {
        return new RemoteComponentEncapsulationDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(RemoteComponentEncapsulationDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsWhenExposingOkHttpClient() {
        lint()
            .files(
                OKHTTP_STUB,
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import okhttp3.OkHttpClient\n" +
                    "interface RemoteComponent {\n" +
                    "    val client: OkHttpClient\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1)
            .expectMatches("RemoteComponent must not expose remote internals \\(okhttp3.OkHttpClient\\)");
    }

    @Test
    public void testReportsWhenExposingPlayIntegrityManager() {
        lint()
            .files(
                PLAY_INTEGRITY_STUB,
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.google.android.play.core.integrity.StandardIntegrityManager\n" +
                    "interface RemoteComponent {\n" +
                    "    fun manager(): StandardIntegrityManager\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1)
            .expectMatches("RemoteComponent must not expose remote internals \\(com.google.android.play.core.integrity.StandardIntegrityManager\\)");
    }

    @Test
    public void testReportsWhenExposingJson() {
        lint()
            .files(
                JSON_STUB,
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import kotlinx.serialization.json.Json\n" +
                    "interface RemoteComponent {\n" +
                    "    val json: Json\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1)
            .expectMatches("RemoteComponent must not expose remote internals \\(kotlinx.serialization.json.Json\\)");
    }

    @Test
    public void testCleanWhenExposingFeedbackService() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.feedback\n" +
                    "interface FeedbackService\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.hluhovskyi.zero.feedback.FeedbackService\n" +
                    "interface RemoteComponent {\n" +
                    "    val feedbackService: FeedbackService\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testCleanWhenOkHttpUsedInOtherInterface() {
        lint()
            .files(
                OKHTTP_STUB,
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import okhttp3.OkHttpClient\n" +
                    "interface OtherComponent {\n" +
                    "    val client: OkHttpClient\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
