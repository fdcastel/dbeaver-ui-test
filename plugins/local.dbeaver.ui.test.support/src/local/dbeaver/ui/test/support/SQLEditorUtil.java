package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.swt.SWT;

public class SQLEditorUtil {

    public static final String PARAM_DIALOG_TITLE = "Bind parameter(s)";

    /**
     * Opens a SQL console for a connection via the navigator context menu.
     * Handles the driver download dialog if needed.
     */
    public static SWTBotEditor openSQLConsole(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.select();
        connNode.contextMenu("SQL Editor").menu("Open SQL console").click();
        sleep(300);

        // Handle driver download dialog if it appears (first time after fresh workspace)
        ConnectionUtil.handleDriverDownload(bot);

        // Wait for the editor to open
        final SWTWorkbenchBot wbot = bot;
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                try {
                    return wbot.activeEditor() != null && getActiveTextEditor(wbot) != null;
                } catch (WidgetNotFoundException e) {
                    return false;
                }
            }
            @Override
            public String getFailureMessage() {
                return "SQL editor did not open in time";
            }
        }, 15000, 300);
        sleep(300);
        return bot.activeEditor();
    }

    /**
     * Types SQL into the active SQL editor, replacing any existing content.
     */
    public static void typeSQL(SWTWorkbenchBot bot, String sql) {
        SWTBotEclipseEditor editor = getActiveTextEditor(bot);
        editor.setFocus();
        editor.setText(sql);
    }

    /** Executes the current SQL statement using Ctrl+Enter. */
    public static void executeStatement(SWTWorkbenchBot bot) {
        SWTBotEclipseEditor editor = getActiveTextEditor(bot);
        editor.setFocus();
        editor.pressShortcut(SWT.CTRL, SWT.CR);
    }

    /** Executes the entire SQL script using Alt+X. */
    public static void executeScript(SWTWorkbenchBot bot) {
        SWTBotEclipseEditor editor = getActiveTextEditor(bot);
        editor.setFocus();
        editor.pressShortcut(SWT.ALT, 'x');
    }

    /**
     * Fills the parameter binding dialog with the given values and clicks OK.
     */
    public static void fillParameterDialog(SWTWorkbenchBot bot, String... values) {
        bot.waitUntil(Conditions.shellIsActive(PARAM_DIALOG_TITLE), 10000);
        SWTBotShell paramShell = bot.shell(PARAM_DIALOG_TITLE);
        paramShell.activate();
        SWTBot dialogBot = paramShell.bot();

        SWTBotTable paramTable = dialogBot.table();
        for (int i = 0; i < values.length; i++) {
            paramTable.click(i, 2); // column 2 = Value
            sleep(100);
            dialogBot.text().setText(values[i]);
            sleep(50);
        }

        dialogBot.button("OK").click();
    }

    /** Checks if the parameter binding dialog is currently open. */
    public static boolean isParameterDialogOpen(SWTWorkbenchBot bot) {
        try {
            bot.shell(PARAM_DIALOG_TITLE);
            return true;
        } catch (WidgetNotFoundException e) {
            return false;
        }
    }

    /**
     * Waits for SQL execution to complete.
     * Checks that no SQL-related jobs are running (rather than all jobs).
     */
    public static void waitForExecution(SWTWorkbenchBot bot) {
        sleep(1000); // initial delay for the execution to start
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                var jobs = org.eclipse.core.runtime.jobs.Job.getJobManager().find(null);
                for (var job : jobs) {
                    String name = job.getName().toLowerCase();
                    if (name.contains("sql") || name.contains("script") || name.contains("query")
                            || name.contains("execute") || name.contains("data read")) {
                        if (job.getState() == org.eclipse.core.runtime.jobs.Job.RUNNING) {
                            return false;
                        }
                    }
                }
                return true;
            }

            @Override
            public String getFailureMessage() {
                return "SQL execution did not complete in time";
            }
        }, 30000, 300);
        sleep(500); // allow results to render
    }

    /**
     * Checks if an error dialog appeared. Returns the error message or null.
     * Walks the SWT widget tree of each matching shell so we can read body text
     * from Text/Label/CLabel/StyledText/Link widgets — DBeaver's ErrorDialog
     * places the real message in a styled Text area, not in label(0).
     */
    public static String checkForErrorDialog(SWTWorkbenchBot bot) {
        for (SWTBotShell shell : bot.shells()) {
            if (!shell.isOpen() || !shell.isVisible()) continue;
            String title = shell.getText().toLowerCase();
            if (title.contains("error") || title.contains("warning")) {
                String body = readShellTextContent(shell);
                shell.close();
                return body == null || body.isEmpty() ? shell.getText() : body;
            }
        }
        return null;
    }

    /**
     * Reads concatenated text content from all text-bearing widgets in the
     * shell's tree. Safe to call on any dialog.
     */
    private static String readShellTextContent(SWTBotShell shell) {
        StringBuilder sb = new StringBuilder();
        org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
            try {
                collectText((org.eclipse.swt.widgets.Widget) shell.widget, sb);
            } catch (Exception ignore) { }
        });
        return sb.toString();
    }

    private static void collectText(org.eclipse.swt.widgets.Widget w, StringBuilder sb) {
        if (w == null || w.isDisposed()) return;
        String text = null;
        if (w instanceof org.eclipse.swt.widgets.Label l) text = l.getText();
        else if (w instanceof org.eclipse.swt.widgets.Text t) text = t.getText();
        else if (w instanceof org.eclipse.swt.widgets.Link lk) text = lk.getText();
        else if (w instanceof org.eclipse.swt.custom.CLabel cl) text = cl.getText();
        else if (w instanceof org.eclipse.swt.custom.StyledText st) text = st.getText();
        if (text != null && !text.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(text);
        }
        if (w instanceof org.eclipse.swt.widgets.Composite c) {
            for (org.eclipse.swt.widgets.Control child : c.getChildren()) {
                collectText(child, sb);
            }
        }
    }

    /** Dismisses any error dialog that might have appeared. */
    public static void dismissErrorDialogs(SWTWorkbenchBot bot) {
        for (SWTBotShell shell : bot.shells()) {
            String title = shell.getText().toLowerCase();
            if (title.contains("error") || title.contains("warning")) {
                shell.close();
            }
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static SWTBotEclipseEditor getActiveTextEditor(SWTWorkbenchBot bot) {
        final SWTWorkbenchBot workbenchBot = bot;
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() {
                try {
                    SWTBotEditor activeEditor = workbenchBot.activeEditor();
                    if (activeEditor == null) {
                        return false;
                    }
                    activeEditor.show();
                    activeEditor.setFocus();
                    return activeEditor.toTextEditor().getStyledText() != null;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "Active SQL text editor did not become ready in time";
            }
        }, 15000, 300);

        SWTBotEditor activeEditor = workbenchBot.activeEditor();
        activeEditor.show();
        activeEditor.setFocus();
        return activeEditor.toTextEditor();
    }
}
