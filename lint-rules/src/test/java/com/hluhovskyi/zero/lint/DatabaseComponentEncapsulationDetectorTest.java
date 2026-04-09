package com.hluhovskyi.zero.lint;

import com.android.tools.lint.checks.infrastructure.LintDetectorTest;
import com.android.tools.lint.checks.infrastructure.TestFile;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import org.junit.Test;
import java.util.Collections;
import java.util.List;

public class DatabaseComponentEncapsulationDetectorTest extends LintDetectorTest {

    private static final TestFile ROOM_ANNOTATIONS_STUBS = java(
        "package androidx.room;\n" +
        "public @interface Dao {}\n" +
        "public @interface Entity {}\n"
    );

    @Override
    protected Detector getDetector() {
        return new DatabaseComponentEncapsulationDetector();
    }

    @Override
    protected List<Issue> getIssues() {
        return Collections.singletonList(DatabaseComponentEncapsulationDetector.Companion.getISSUE());
    }

    @Test
    public void testReportsWhenExposedInterfaceIsAnnotatedWithDao() {
        lint()
            .files(
                ROOM_ANNOTATIONS_STUBS,
                kotlin(
                    "package com.hluhovskyi.zero.accounts\n" +
                    "import androidx.room.Dao\n" +
                    "@Dao\n" +
                    "interface AccountDao\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.hluhovskyi.zero.accounts.AccountDao\n" +
                    "interface DatabaseComponent {\n" +
                    "    val accountDao: AccountDao\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1)
            .expectMatches("DatabaseComponent must not expose database internals \\(AccountDao\\)");
    }

    @Test
    public void testReportsWhenExposedClassIsAnnotatedWithEntity() {
        lint()
            .files(
                ROOM_ANNOTATIONS_STUBS,
                kotlin(
                    "package com.hluhovskyi.zero.accounts\n" +
                    "import androidx.room.Entity\n" +
                    "@Entity\n" +
                    "class AccountEntity\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.hluhovskyi.zero.accounts.AccountEntity\n" +
                    "interface DatabaseComponent {\n" +
                    "    val account: AccountEntity\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1)
            .expectMatches("DatabaseComponent must not expose database internals \\(AccountEntity\\)");
    }

    @Test
    public void testReportsWhenExposedEntityViaMethod() {
        lint()
            .files(
                ROOM_ANNOTATIONS_STUBS,
                kotlin(
                    "package com.hluhovskyi.zero.common\n" +
                    "import androidx.room.Entity\n" +
                    "@Entity\n" +
                    "class RateEntity\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.hluhovskyi.zero.common.RateEntity\n" +
                    "interface DatabaseComponent {\n" +
                    "    fun rate(): RateEntity\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectErrorCount(1)
            .expectMatches("DatabaseComponent must not expose database internals \\(RateEntity\\)");
    }

    @Test
    public void testCleanWhenNoDaosOrEntitiesAreExposed() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "interface DatabaseComponent {\n" +
                    "    val accountRepository: String\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testCleanWhenInterfaceIsNotAnnotated() {
        lint()
            .files(
                kotlin(
                    "package com.hluhovskyi.zero.accounts\n" +
                    "interface AccountRepository\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.hluhovskyi.zero.accounts.AccountRepository\n" +
                    "interface DatabaseComponent {\n" +
                    "    val accountRepository: AccountRepository\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }

    @Test
    public void testCleanWhenEntityIsUsedInNonDatabaseComponent() {
        lint()
            .files(
                ROOM_ANNOTATIONS_STUBS,
                kotlin(
                    "package com.hluhovskyi.zero.common\n" +
                    "import androidx.room.Entity\n" +
                    "@Entity\n" +
                    "class RateEntity\n"
                ).indented(),
                kotlin(
                    "package com.hluhovskyi.zero\n" +
                    "import com.hluhovskyi.zero.common.RateEntity\n" +
                    "interface OtherComponent {\n" +
                    "    val rate: RateEntity\n" +
                    "}\n"
                ).indented()
            )
            .run()
            .expectClean();
    }
}
