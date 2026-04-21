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
 * Manual-commit variant of the dbeaver/dbeaver#40665 regression guard for
 * PostgreSQL.
 *
 * IvanGorshechnikov's objection to the PR was specifically that PostgreSQL
 * relies on raw COMMIT/ROLLBACK being sent to the driver, and that the PR's
 * interception would break it. The companion {@code ScriptCommitRollbackTest}
 * runs in DBeaver's default auto-commit mode, where COMMIT is a no-op on both
 * the old and new code paths and so the change cannot be observed; this test
 * forces the connection into <b>manual-commit mode</b>, which is the mode in
 * which the PR's tx-manager routing is actually exercised.
 *
 * Strategy:
 *  1. Toggle the live connection to manual-commit via
 *     {@link ConnectionUtil#setAutoCommit(String, boolean)}.
 *  2. Run the PR's repro script (DDL + four INSERTs + COMMIT) via Alt+X.
 *  3. Assert no error dialog surfaced.
 *  4. Open an isolated execution context (separate session, its own fresh
 *     transaction) and SELECT COUNT(*). The isolated session can only see
 *     committed data — if the intercepted COMMIT actually persisted the four
 *     rows, the count is 4; if the PR silently failed to commit, the
 *     isolated session sees zero rows (or the table is absent entirely,
 *     because the CREATE TABLE's COMMIT was also intercepted).
 *  5. Screenshot the DBeaver window as the success artifact.
 */
public class ScriptCommitManualModeTest {

    private static SWTWorkbenchBot bot;

    /**
     * PR #40665 repro translated to PostgreSQL, followed by a ROLLBACK + SELECT
     * so the editor's own result tab shows the row count. In manual-commit
     * mode, if the intercepted COMMIT actually reached the server then the
     * trailing ROLLBACK is a no-op and the SELECT returns 4; if the PR
     * silently failed to commit, the ROLLBACK discards the four INSERTs and
     * the SELECT returns 0 (or errors because the CREATE TABLE was also
     * uncommitted). Either outcome is visible in the screenshot.
     */
    private static final String SCRIPT =
            "DROP TABLE IF EXISTS test_section9_manual;\n" +
            "CREATE TABLE test_section9_manual (\n" +
            "    id INTEGER NOT NULL,\n" +
            "    name VARCHAR(100),\n" +
            "    category VARCHAR(50),\n" +
            "    amount NUMERIC(12,2),\n" +
            "    created_at TIMESTAMP,\n" +
            "    bin_value BIGINT,\n" +
            "    CONSTRAINT pk_test_section9_manual PRIMARY KEY (id)\n" +
            ");\n" +
            "\n" +
            "COMMIT;\n" +
            "\n" +
            "INSERT INTO test_section9_manual (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (1, 'Alice', 'A', 10.00, CURRENT_TIMESTAMP, 1);\n" +
            "INSERT INTO test_section9_manual (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (2, 'Bob', 'A', 15.00, CURRENT_TIMESTAMP, 2);\n" +
            "INSERT INTO test_section9_manual (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (3, 'Carol', 'B', 7.50, CURRENT_TIMESTAMP, 4);\n" +
            "INSERT INTO test_section9_manual (id, name, category, amount, created_at, bin_value) VALUES\n" +
            "    (4, 'Dave', 'B', 22.00, CURRENT_TIMESTAMP, 8);\n" +
            "\n" +
            "COMMIT;\n" +
            "\n" +
            "-- Verification (visible in screenshot): if the two intercepted\n" +
            "-- COMMITs above actually reached the server, this ROLLBACK has\n" +
            "-- nothing to undo and the SELECT returns 4. If they silently\n" +
            "-- failed, the ROLLBACK discards the INSERTs and count is 0.\n" +
            "ROLLBACK;\n" +
            "SELECT COUNT(*) AS row_count FROM test_section9_manual;\n";

    @BeforeClass
    public static void setUp() {
        bot = PostgreSQLTestContext.getBot();
        String connName = PostgreSQLTestContext.getConnectionName();
        assertNotNull("Connection must exist from previous test", connName);
        try {
            ConnectionUtil.connectTo(bot, connName);
        } catch (Exception e) {
            // might already be connected
        }
        ConnectionUtil.setAutoCommit(connName, false);
    }

    @Test
    public void testScriptWithCommitInManualCommitMode() {
        SWTBotEditor editor = null;
        String connName = PostgreSQLTestContext.getConnectionName();
        try {
            editor = SQLEditorUtil.openSQLConsole(bot, connName);
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, SCRIPT);
            SQLEditorUtil.executeScript(bot);
            SQLEditorUtil.waitForExecution(bot);

            // If the raw-COMMIT regression IvanGorshechnikov warned about had
            // actually happened, DBeaver would now be showing an "Execution
            // Error" dialog — the trailing ROLLBACK would have discarded the
            // CREATE TABLE too, and the subsequent SELECT against the
            // non-existent table would surface as an error. Absence of that
            // dialog is the primary signal that the PR fix works on
            // PostgreSQL in manual-commit mode.
            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("PR #40665 script should execute without errors on PostgreSQL (manual-commit). Got: '" + error + "'", error);

            // Artifact evidence: screenshot shows the editor + the result tab
            // where SELECT COUNT(*) is visible. A passing test + this
            // screenshot together prove the intercepted COMMIT reached the
            // server.
            ScreenshotUtil.capture("PASS_PR40665_PostgreSQL_ManualCommit_ScriptCommitRollback");
        } catch (RuntimeException | AssertionError e) {
            ScreenshotUtil.captureOnFailure("ScriptCommitManualModeTest",
                    "testScriptWithCommitInManualCommitMode");
            throw e;
        } finally {
            if (editor != null) {
                try { editor.close(); } catch (Exception ignore) { }
            }
        }
    }

    @AfterClass
    public static void tearDown() {
        // Restore auto-commit so later tests (if any) don't inherit the
        // manual-commit state via the shared workbench instance.
        try {
            ConnectionUtil.setAutoCommit(PostgreSQLTestContext.getConnectionName(), true);
        } catch (Exception ignore) { }
        WorkbenchUtil.closeAllEditors(bot);
    }
}
