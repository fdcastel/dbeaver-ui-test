package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.swt.SWT;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;

public class SQLEditorUtil {

    /**
     * Opens a SQL editor for a connection by right-clicking in the navigator.
     */
    public static SWTBotEditor openSQLEditor(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.select();
        connNode.contextMenu("SQL Editor").click();

        // Wait for editor to open
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return bot.activeEditor();
    }

    /**
     * Types SQL into the active SQL editor.
     */
    public static void typeSQL(SWTWorkbenchBot bot, String sql) {
        SWTBotStyledText styledText = bot.styledText();
        styledText.setText(sql);
    }

    /**
     * Executes the SQL script in the active editor using Ctrl+Enter (execute statement)
     * or Alt+X (execute script).
     */
    public static void executeStatement(SWTWorkbenchBot bot) {
        bot.styledText().setFocus();
        // Ctrl+Enter executes the current statement
        bot.styledText().pressShortcut(SWT.CTRL, SWT.CR);
    }

    /**
     * Executes the entire SQL script using Alt+X.
     */
    public static void executeScript(SWTWorkbenchBot bot) {
        bot.styledText().setFocus();
        bot.styledText().pressShortcut(SWT.ALT, 'x');
    }

    /**
     * Fills parameter values in the parameter binding dialog.
     * @param bot the workbench bot
     * @param values parameter values in order
     */
    public static void fillParameterDialog(SWTWorkbenchBot bot, String... values) {
        SWTBotShell paramShell = bot.shell("Bind parameter(s)");
        paramShell.activate();
        SWTBot dialogBot = paramShell.bot();

        SWTBotTable paramTable = dialogBot.table();
        for (int i = 0; i < values.length; i++) {
            paramTable.click(i, 1); // Click the Value column
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            dialogBot.text().setText(values[i]);
        }

        dialogBot.button("OK").click();
    }

    /**
     * Checks whether the result panel contains the expected text.
     * Looks in the Data tab of the result panel.
     */
    public static boolean resultContains(SWTWorkbenchBot bot, String expected) {
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try {
            // Look for the result in the status line or result grid
            SWTBotStyledText outputText = bot.styledText(1);
            return outputText.getText().contains(expected);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Waits for SQL execution to complete by checking for the result panel.
     */
    public static void waitForExecution(SWTWorkbenchBot bot) {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                // Execution is done when background jobs finish
                return org.eclipse.core.runtime.jobs.Job.getJobManager()
                        .find(null).length == 0;
            }

            @Override
            public String getFailureMessage() {
                return "SQL execution did not complete in time";
            }
        }, 30000, 500);
    }

    /**
     * Checks if an error dialog appeared during execution.
     */
    public static boolean hasErrorDialog(SWTWorkbenchBot bot) {
        try {
            bot.shell("Error").close();
            return true;
        } catch (WidgetNotFoundException e) {
            return false;
        }
    }
}
