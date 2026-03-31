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
 * EXECUTE BLOCK with Named Parameters Test
 *
 * Verifies that DBeaver correctly handles NAMED parameter binding (:x, :y)
 * for Firebird's EXECUTE BLOCK statements, as opposed to positional (?) params.
 *
 * Expected behavior:
 * - FAIL on 'devel' branch (named params not handled by native binding)
 * - PASS on 'fix/firebird-execute-block-params' branch (named binding support)
 */
public class ExecuteBlockNamedParamsTest {

    private static SWTWorkbenchBot bot;

    /** Uses :x and :y named parameters instead of positional ? */
    private static final String EXECUTE_BLOCK_NAMED_SQL =
            "EXECUTE BLOCK (x DOUBLE PRECISION = :x, y DOUBLE PRECISION = :y)\n" +
            "RETURNS (gmean DOUBLE PRECISION)\n" +
            "AS\n" +
            "BEGIN\n" +
            "  gmean = SQRT(x * y);\n" +
            "  SUSPEND;\n" +
            "END";

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
    public void testExecuteBlockWithNamedParameters() {
        try {
            SWTBotEditor editor = SQLEditorUtil.openSQLConsole(bot,
                    FirebirdTestContext.getConnectionName());
            assertNotNull("SQL editor should open", editor);

            SQLEditorUtil.typeSQL(bot, EXECUTE_BLOCK_NAMED_SQL);
            SQLEditorUtil.executeStatement(bot);

            // Wait for parameter dialog
            bot.waitUntil(Conditions.shellIsActive(SQLEditorUtil.PARAM_DIALOG_TITLE), 15000);
            assertTrue("Parameter binding dialog should appear",
                    SQLEditorUtil.isParameterDialogOpen(bot));

            // Fill parameter values: x=9, y=16 → gmean = SQRT(144) = 12.0
            SQLEditorUtil.fillParameterDialog(bot, "9", "16");

            SQLEditorUtil.waitForExecution(bot);

            // On 'devel' branch this FAILS because named params are not replaced
            // with positional ? placeholders before JDBC execution.
            // On 'fix/firebird-execute-block-params' branch, named binding succeeds.
            String error = SQLEditorUtil.checkForErrorDialog(bot);
            assertNull("EXECUTE BLOCK with named params should execute without errors " +
                    "(fails on 'devel', passes on 'fix/firebird-execute-block-params'). " +
                    "Error: " + error, error);

            editor.close();
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("ExecuteBlockNamedParamsTest",
                    "testExecuteBlockWithNamedParameters");
            throw e;
        }
    }

    @AfterClass
    public static void tearDown() {
        SQLEditorUtil.dismissErrorDialogs(bot);
        WorkbenchUtil.closeAllEditors(bot);
    }
}
