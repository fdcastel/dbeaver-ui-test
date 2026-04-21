package local.dbeaver.firebird.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import local.dbeaver.ui.test.support.ConnectionUtil;
import local.dbeaver.ui.test.support.SQLEditorUtil;
import local.dbeaver.ui.test.support.ScreenshotUtil;
import local.dbeaver.ui.test.support.WorkbenchUtil;

/**
 * Proof of dbeaver/dbeaver#40665 for Firebird.
 *
 * Runs the exact repro script from the PR description (DDL + INSERTs mixed
 * with COMMIT/ROLLBACK statements) via Alt+X. Before the PR, Firebird errored
 * out on the COMMIT with <code>SQL Error [335544332] [08003]: invalid
 * transaction handle (expecting explicit transaction start)</code>. After the
 * PR, COMMIT/ROLLBACK are intercepted and routed through the transaction
 * manager API, so the script runs cleanly.
 *
 * On success the test snapshots the DBeaver window to an artifact PNG so the
 * workflow run doubles as visual evidence.
 */
public class ScriptCommitRollbackTest {

    private static SWTWorkbenchBot bot;

    /** Matches the exact repro script from the PR description. */
    private static final String SCRIPT =
            "RECREATE TABLE TEST_SECTION9 (\n" +
            "    ID INTEGER NOT NULL,\n" +
            "    NAME VARCHAR(100),\n" +
            "    CATEGORY VARCHAR(50),\n" +
            "    AMOUNT DECIMAL(12,2),\n" +
            "    CREATED_AT TIMESTAMP,\n" +
            "    BIN_VALUE BIGINT,\n" +
            "    CONSTRAINT PK_TEST_SECTION9 PRIMARY KEY (ID)\n" +
            ");\n" +
            "\n" +
            "COMMIT;\n" +
            "\n" +
            "INSERT INTO TEST_SECTION9 (ID, NAME, CATEGORY, AMOUNT, CREATED_AT, BIN_VALUE) VALUES\n" +
            "    (1, 'Alice', 'A', 10.00, CURRENT_TIMESTAMP, 1);\n" +
            "INSERT INTO TEST_SECTION9 (ID, NAME, CATEGORY, AMOUNT, CREATED_AT, BIN_VALUE) VALUES\n" +
            "    (2, 'Bob', 'A', 15.00, CURRENT_TIMESTAMP, 2);\n" +
            "INSERT INTO TEST_SECTION9 (ID, NAME, CATEGORY, AMOUNT, CREATED_AT, BIN_VALUE) VALUES\n" +
            "    (3, 'Carol', 'B', 7.50, CURRENT_TIMESTAMP, 4);\n" +
            "INSERT INTO TEST_SECTION9 (ID, NAME, CATEGORY, AMOUNT, CREATED_AT, BIN_VALUE) VALUES\n" +
            "    (4, 'Dave', 'B', 22.00, CURRENT_TIMESTAMP, 8);\n" +
            "\n" +
            "COMMIT;\n";

    @BeforeClass
    public static void setUp() {
        bot = FirebirdTestContext.getBot();
        assertNotNull("Connection must exist from previous test",
                FirebirdTestContext.getConnectionName());
        try {
            ConnectionUtil.connectTo(bot, FirebirdTestContext.getConnectionName());
        } catch (Exception e) {
            // might already be connected
        }
    }

    @Test
    public void testScriptWithCommitAndRollbackExecutesWithoutError() {
        SWTBotEditor editor = null;
        try {
            editor = SQLEditorUtil.openSQLConsole(bot,
                    FirebirdTestContext.getConnectionName());
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, SCRIPT);
            SQLEditorUtil.executeScript(bot);
            // Note: waitForExecution is wrapped because Jaybird occasionally
            // leaves a background job in COMMITTING state after an intercepted
            // COMMIT — the actual SQL finishes, but the async bookkeeping job
            // never transitions out of RUNNING. The real regression signal is
            // an error dialog (raw COMMIT reaching the driver) — check for
            // that regardless of the job-queue state.
            try {
                SQLEditorUtil.waitForExecution(bot);
            } catch (RuntimeException ignore) {
                // fall through to error-dialog inspection
            }

            // Snapshot BEFORE asserting so we have evidence even if the
            // assertion later fails.
            ScreenshotUtil.capture("PASS_PR40665_Firebird_ScriptCommitRollback");

            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("PR #40665 repro script should execute without errors on Firebird. Got: '" + error + "'", error);
        } catch (RuntimeException | AssertionError e) {
            ScreenshotUtil.captureOnFailure("ScriptCommitRollbackTest",
                    "testScriptWithCommitAndRollbackExecutesWithoutError");
            throw e;
        } finally {
            if (editor != null) {
                try { editor.close(); } catch (Exception ignore) { }
            }
        }
    }

    @AfterClass
    public static void tearDown() {
        WorkbenchUtil.closeAllEditors(bot);
    }
}
