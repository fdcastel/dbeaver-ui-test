package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

public class ConnectionUtil {

    private static final long DIALOG_TIMEOUT = 30000;

    /**
     * Opens the New Database Connection wizard from the menu.
     */
    public static SWTBotShell openNewConnectionWizard(SWTWorkbenchBot bot) {
        bot.menu("Database").menu("New Database Connection").click();
        bot.waitUntil(Conditions.shellIsActive("Connect to a database"));
        return bot.shell("Connect to a database");
    }

    /**
     * Selects a driver in the connection wizard by typing its name in the search field.
     */
    public static void selectDriver(SWTBot wizardBot, String driverName) {
        // Type in the search/filter field at the top of the wizard
        wizardBot.text().setText(driverName);
        // Wait for filter to apply
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        // The filtered list should show the driver; select it
        SWTBotTree tree = wizardBot.tree();
        SWTBotTreeItem[] items = tree.getAllItems();
        for (SWTBotTreeItem category : items) {
            try {
                category.expand();
                for (SWTBotTreeItem driver : category.getItems()) {
                    if (driver.getText().contains(driverName)) {
                        driver.select();
                        return;
                    }
                }
            } catch (WidgetNotFoundException e) {
                // continue searching
            }
        }
        throw new WidgetNotFoundException("Driver '" + driverName + "' not found in connection wizard");
    }

    /**
     * Creates a Firebird connection through the wizard and returns the connection name.
     */
    public static String createFirebirdConnection(SWTWorkbenchBot bot,
            String host, String port, String database, String user, String password) {
        SWTBotShell wizardShell = openNewConnectionWizard(bot);
        SWTBot wizard = wizardShell.bot();

        // Select Firebird driver
        selectDriver(wizard, "Firebird");
        wizard.button("Next >").click();

        // Fill connection details - the generic connection page has labeled text fields
        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        setTextFieldByLabel(wizard, "Host", host);
        setTextFieldByLabel(wizard, "Port", port);
        setTextFieldByLabel(wizard, "Database", database);
        setTextFieldByLabel(wizard, "User name", user);
        setTextFieldByLabel(wizard, "Password", password);

        // Finish the wizard
        wizard.button("Finish").click();

        // Wait for wizard to close
        bot.waitUntil(Conditions.shellCloses(wizardShell), DIALOG_TIMEOUT);

        return "Firebird - " + host;
    }

    /**
     * Opens the Edit Connection dialog for a connection in the navigator.
     */
    public static SWTBotShell openEditConnection(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.select();
        connNode.contextMenu("Edit Connection").click();
        bot.waitUntil(Conditions.shellIsActive("Connection Configuration"));
        return bot.shell("Connection Configuration");
    }

    /**
     * Reads a text field value by its label in a dialog.
     */
    public static String getTextFieldByLabel(SWTBot bot, String label) {
        return bot.textWithLabel(label).getText();
    }

    /**
     * Sets a text field value by its label.
     */
    public static void setTextFieldByLabel(SWTBot bot, String label, String value) {
        try {
            bot.textWithLabel(label).setText(value);
        } catch (WidgetNotFoundException e) {
            // Some fields might use different label patterns; try with ":" suffix
            try {
                bot.textWithLabel(label + ":").setText(value);
            } catch (WidgetNotFoundException e2) {
                throw new WidgetNotFoundException("Text field with label '" + label + "' not found", e2);
            }
        }
    }

    /**
     * Connects to a database by double-clicking the connection in navigator.
     */
    public static void connectTo(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.doubleClick();
        // Wait for connection to establish
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                try {
                    // Check if the node has children (tables, etc.) which means connected
                    connNode.expand();
                    return connNode.getItems().length > 0;
                } catch (Exception e) {
                    return false;
                }
            }

            @Override
            public String getFailureMessage() {
                return "Connection '" + connectionName + "' did not connect in time";
            }
        }, 30000, 1000);
    }

    /**
     * Deletes a connection from the navigator.
     */
    public static void deleteConnection(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.select();
        connNode.contextMenu("Delete").click();
        try {
            SWTBotShell confirmShell = bot.shell("Confirm");
            confirmShell.bot().button("Yes").click();
        } catch (WidgetNotFoundException e) {
            try {
                SWTBotShell confirmShell = bot.shell("Delete");
                confirmShell.bot().button("Yes").click();
            } catch (WidgetNotFoundException e2) {
                // Try OK button
                bot.button("OK").click();
            }
        }
    }
}
