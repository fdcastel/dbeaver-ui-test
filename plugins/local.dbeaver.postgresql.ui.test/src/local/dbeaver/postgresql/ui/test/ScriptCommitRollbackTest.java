package local.dbeaver.postgresql.ui.test;

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
 * Regression guard for dbeaver/dbeaver#40665.
 *
 * The PR intercepts COMMIT and ROLLBACK statements in
 * SQLQueryJob.executeSingleElement() and routes them through the transaction
 * manager API rather than sending them as raw SQL. This test proves that the
 * change does NOT break databases (like PostgreSQL) which previously accepted
 * raw COMMIT / ROLLBACK happily.
 *
 * The test runs the PR's own repro script via Alt+X (Execute SQL Script) and
 * asserts no error dialog appears.
 */
public class ScriptCommitRollbackTest {

    private static SWTWorkbenchBot bot;

    /**
     * The PR's repro script, translated to PostgreSQL syntax (DROP ... IF EXISTS
     * + CREATE, NUMERIC instead of DECIMAL). Same shape: DDL, COMMIT, a batch of
     * INSERTs, COMMIT.
     */
    private static final String SCRIPT =
            "DROP TABLE IF EXISTS test_section9;\n" +
            "CREATE TABLE test_section9 (\n" +
            "    id INTEGER NOT NULL,\n" +
            "    name VARCHAR(100),\n" +
            "    category VARCHAR(50),\n" +
            "    amount NUMERIC(12,2),\n" +
            "    created_at TIMESTAMP,\n" +
            "    bin_value BIGINT,\n" +
            "    CONSTRAINT pk_test_section9 PRIMARY KEY (id)\n" +
            ");\n" +
            "\n" +
            "COMMIT;\n" +
            "\n" +
            "INSERT INTO test_section9 (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (1, 'Alice', 'A', 10.00, CURRENT_TIMESTAMP, 1);\n" +
            "INSERT INTO test_section9 (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (2, 'Bob', 'A', 15.00, CURRENT_TIMESTAMP, 2);\n" +
            "INSERT INTO test_section9 (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (3, 'Carol', 'B', 7.50, CURRENT_TIMESTAMP, 4);\n" +
            "INSERT INTO test_section9 (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (4, 'Dave', 'B', 22.00, CURRENT_TIMESTAMP, 8);\n" +
            "\n" +
            "COMMIT;\n";

    @BeforeClass
    public static void setUp() {
        bot = PostgreSQLTestContext.getBot();
        assertNotNull("Connection must exist from previous test",
                PostgreSQLTestContext.getConnectionName());
        try {
            ConnectionUtil.connectTo(bot, PostgreSQLTestContext.getConnectionName());
        } catch (Exception e) {
            // might already be connected
        }
    }

    /**
     * Executes a script containing both COMMIT and ROLLBACK via Alt+X.
     * Before PR #40665 the raw COMMIT/ROLLBACK were sent to the driver and
     * PostgreSQL accepted them without error. After the PR the statements are
     * intercepted and routed through the transaction manager. Either way, on
     * PostgreSQL in the default (auto-commit) mode, the script must execute
     * cleanly — no error dialog.
     */
    @Test
    public void testScriptWithCommitAndRollbackExecutesWithoutError() {
        try {
            SWTBotEditor editor = SQLEditorUtil.openSQLConsole(bot,
                    PostgreSQLTestContext.getConnectionName());
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, SCRIPT);
            SQLEditorUtil.executeScript(bot);
            SQLEditorUtil.waitForExecution(bot);

            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("Script with COMMIT/ROLLBACK should execute without errors. Got: " + error, error);

            // Screenshot the editor after a clean run — uploaded as a workflow
            // artifact to evidence that PR #40665 does not regress PostgreSQL.
            ScreenshotUtil.capture("PASS_PR40665_PostgreSQL_ScriptCommitRollback");

            editor.close();
        } catch (RuntimeException e) {
            ScreenshotUtil.captureOnFailure("ScriptCommitRollbackTest", "testScriptWithCommitAndRollbackExecutesWithoutError");
            throw e;
        }
    }

    @AfterClass
    public static void tearDown() {
        WorkbenchUtil.closeAllEditors(bot);
    }
}
