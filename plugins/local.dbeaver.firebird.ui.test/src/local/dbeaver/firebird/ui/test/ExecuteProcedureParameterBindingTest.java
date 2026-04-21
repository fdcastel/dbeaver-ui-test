package local.dbeaver.firebird.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import local.dbeaver.ui.test.support.ConnectionUtil;
import local.dbeaver.ui.test.support.SQLEditorUtil;
import local.dbeaver.ui.test.support.ScreenshotUtil;
import local.dbeaver.ui.test.support.WorkbenchUtil;

/**
 * EXECUTE PROCEDURE Parameter Binding Test
 *
 * Guards the regression path unlocked by PR #40644. Before the fix,
 * FireBirdSQLDialect classified EXECUTE as a DDL keyword, so
 * SQLScriptParser skipped ?-parameter detection for any statement
 * starting with EXECUTE — including EXECUTE PROCEDURE. The ? reached
 * Firebird unbound and failed with "Invalid number of parameters".
 *
 * This test exercises the stored procedure path (distinct from the
 * EXECUTE BLOCK path covered by ExecuteBlockParameterBindingTest) so
 * that re-introducing EXECUTE into DDL_KEYWORDS — or any equivalent
 * regression — is caught here.
 *
 * Expected behavior:
 * - FAIL on 'devel' branch (EXECUTE in DDL_KEYWORDS → no parameter dialog, raw ? sent)
 * - PASS on 'fix/firebird-execute-block-params' branch (EXECUTE removed from DDL_KEYWORDS)
 */
public class ExecuteProcedureParameterBindingTest {

    private static SWTWorkbenchBot bot;

    /** Invokes SP_GET_CUSTOMER(P_ID) from the fixture. Expects customer id 1 = 'Alice'. */
    private static final String EXECUTE_PROCEDURE_SQL =
            "EXECUTE PROCEDURE SP_GET_CUSTOMER(?)";

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
    public void testExecuteProcedureWithParameter() {
        try {
            SWTBotEditor editor = SQLEditorUtil.openSQLConsole(bot,
                    FirebirdTestContext.getConnectionName());
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, EXECUTE_PROCEDURE_SQL);
            SQLEditorUtil.executeStatement(bot);

            // Parameter dialog must appear — this is the core regression signal:
            // if EXECUTE is classified as DDL, the ?-detection is skipped and no
            // dialog shows up at all.
            bot.waitUntil(Conditions.shellIsActive(SQLEditorUtil.PARAM_DIALOG_TITLE), 15000);
            assertTrue("Parameter binding dialog should appear for EXECUTE PROCEDURE(?)",
                    SQLEditorUtil.isParameterDialogOpen(bot));

            // Bind P_ID = 1 → returns Alice.
            SQLEditorUtil.fillParameterDialog(bot, "1");

            SQLEditorUtil.waitForExecution(bot);

            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("EXECUTE PROCEDURE with ? should execute without errors " +
                    "(fails if EXECUTE is in DDL_KEYWORDS). Error: " + error, error);

            editor.close();
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("ExecuteProcedureParameterBindingTest",
                    "testExecuteProcedureWithParameter");
            throw e;
        }
    }

    @AfterClass
    public static void tearDown() {
        SQLEditorUtil.dismissErrorDialogs(bot);
        WorkbenchUtil.closeAllEditors(bot);
    }
}
