package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.swt.widgets.Display;

/**
 * Connection management utilities.
 *
 * Key insight from Invoke-DBeaver.ps1: connections can be created by writing
 * directly to data-sources.json, completely bypassing the wizard UI.
 *
 * For test setup, we use the DBeaver API to register connections programmatically.
 * The wizard UI is only exercised by tests that specifically test the wizard.
 */
public class ConnectionUtil {

    public static final String WIZARD_TITLE = "Connect to a database";

    // ── Programmatic connection creation (fast, for test setup) ──────────

    /**
     * Generic: creates a DBeaver connection programmatically, no wizard UI.
     * {@code driverSearchTerm} is a lowercase substring used to locate the driver
     * by id (primary) or name (fallback) — e.g. "jaybird"/"firebird" or "postgres".
     * {@code connectionDisplayName} becomes the datasource name shown in the navigator.
     */
    public static String createConnectionViaAPI(
            String driverSearchTerm, String connectionDisplayName,
            String host, String port, String database, String user, String password) {

        final String[] result = { null };
        Display.getDefault().syncExec(() -> {
            try {
                var project = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform()
                        .getWorkspace().getActiveProject();
                if (project == null) {
                    System.err.println("ConnectionUtil: no active project");
                    return;
                }

                var registry = project.getDataSourceRegistry();
                var driverDescriptor = findDriver(driverSearchTerm);
                if (driverDescriptor == null) {
                    System.err.println("ConnectionUtil: driver not found for term: " + driverSearchTerm);
                    return;
                }

                var config = new org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration();
                config.setHostName(host);
                config.setHostPort(port);
                config.setDatabaseName(database);
                config.setUserName(user);
                config.setUserPassword(password);

                var dsContainer = registry.createDataSource(driverDescriptor, config);
                dsContainer.setName(connectionDisplayName);
                dsContainer.setSavePassword(true);
                registry.addDataSource(dsContainer);
                registry.flushConfig();

                result[0] = dsContainer.getName();
            } catch (Exception e) {
                System.err.println("ConnectionUtil: API connection creation failed: " + e);
                e.printStackTrace(System.err);
            }
        });

        sleep(200);
        return result[0];
    }

    /** Firebird-specific wrapper. */
    public static String createFirebirdConnectionViaAPI(
            String host, String port, String database, String user, String password) {
        return createConnectionViaAPI("jaybird", "Firebird", host, port, database, user, password);
    }

    /** PostgreSQL-specific wrapper. */
    public static String createPostgreSQLConnectionViaAPI(
            String host, String port, String database, String user, String password) {
        return createConnectionViaAPI("postgres", "PostgreSQL", host, port, database, user, password);
    }

    /**
     * Updates an existing connection's settings via the DBeaver API.
     * Matches the datasource whose name contains {@code connName} (or, as a
     * fallback, whose driver name contains {@code driverSearchTerm}, lower-cased).
     */
    public static void updateConnectionViaAPI(
            String connName, String driverSearchTerm,
            String host, String port, String database, String user, String password) {
        final String term = driverSearchTerm == null ? "" : driverSearchTerm.toLowerCase();
        Display.getDefault().syncExec(() -> {
            try {
                var project = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform()
                        .getWorkspace().getActiveProject();
                if (project == null) return;

                var registry = project.getDataSourceRegistry();
                for (var ds : registry.getDataSources()) {
                    if (ds.getName().contains(connName)
                            || (!term.isEmpty() && ds.getDriver().getName().toLowerCase().contains(term))) {
                        var config = ds.getConnectionConfiguration();
                        config.setHostName(host);
                        config.setHostPort(port);
                        config.setDatabaseName(database);
                        config.setUserName(user);
                        config.setUserPassword(password);
                        ds.persistConfiguration();
                        registry.flushConfig();
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        });
    }

    /**
     * Toggles auto-commit on the live JDBC connection via the DBeaver
     * transaction manager. Use this to switch a connected datasource between
     * auto- and manual-commit mode without reconnecting — subsequent SQL
     * editor operations will run under the new mode.
     */
    public static void setAutoCommit(String connName, boolean autoCommit) {
        Display.getDefault().syncExec(() -> {
            try {
                var ds = findContainer(connName);
                if (ds == null || !ds.isConnected() || ds.getDataSource() == null) {
                    System.err.println("ConnectionUtil.setAutoCommit: datasource not connected for " + connName);
                    return;
                }
                var monitor = new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor();
                var ctx = ds.getDataSource().getDefaultInstance().getDefaultContext(monitor, false);
                var tx = org.jkiss.dbeaver.model.DBUtils.getTransactionManager(ctx);
                if (tx != null) {
                    tx.setAutoCommit(monitor, autoCommit);
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        });
    }

    /**
     * Runs {@code sql} against an <b>isolated</b> execution context opened from
     * the explicit target database (not the datasource's default instance —
     * for multi-database drivers like PostgreSQL that's ambiguous). Returns
     * the first column of the first row as a {@code long}, or {@code -1} on
     * empty result / failure. An isolated context has its own transaction, so
     * it only sees committed data.
     *
     * <p>{@code targetDbName} is looked up in the datasource's catalog/schema
     * tree and matched case-insensitively against {@code getName()}.
     */
    public static long queryScalarLongIsolated(String connName, String targetDbName, String sql) {
        final long[] result = { -1 };
        final String[] diag = { null };
        Display.getDefault().syncExec(() -> {
            try {
                var ds = findContainer(connName);
                if (ds == null || !ds.isConnected() || ds.getDataSource() == null) {
                    diag[0] = "datasource not connected";
                    return;
                }
                var monitor = new org.jkiss.dbeaver.model.runtime.VoidProgressMonitor();

                // Pick the correct DBSInstance — for PostgreSQL getDefaultInstance()
                // returns the initial-database instance, but we want the one the
                // SQL editor was executing against (ui_test, not postgres).
                org.jkiss.dbeaver.model.struct.DBSInstance instance = findInstance(
                        ds.getDataSource(), targetDbName, monitor);
                if (instance == null) {
                    diag[0] = "instance '" + targetDbName + "' not found; trying default";
                    instance = ds.getDataSource().getDefaultInstance();
                }

                org.jkiss.dbeaver.model.exec.DBCExecutionContext isolated =
                        instance.openIsolatedContext(monitor, "ui-test verify", null);
                try {
                    try (org.jkiss.dbeaver.model.exec.DBCSession session = isolated.openSession(
                            monitor, org.jkiss.dbeaver.model.exec.DBCExecutionPurpose.USER, "verify")) {
                        try (org.jkiss.dbeaver.model.exec.DBCStatement stmt = session.prepareStatement(
                                org.jkiss.dbeaver.model.exec.DBCStatementType.QUERY, sql, false, false, false)) {
                            stmt.executeStatement();
                            try (org.jkiss.dbeaver.model.exec.DBCResultSet rs = stmt.openResultSet()) {
                                if (rs.nextRow()) {
                                    Object v = rs.getAttributeValue(0);
                                    if (v instanceof Number n) result[0] = n.longValue();
                                }
                            }
                        }
                    }
                } finally {
                    try { isolated.close(); } catch (Exception ignore) { }
                }
            } catch (Exception e) {
                diag[0] = "exception: " + e.getMessage();
                e.printStackTrace(System.err);
            }
        });
        if (diag[0] != null) {
            System.err.println("ConnectionUtil.queryScalarLongIsolated: " + diag[0]);
        }
        return result[0];
    }

    private static org.jkiss.dbeaver.model.struct.DBSInstance findInstance(
            org.jkiss.dbeaver.model.DBPDataSource dataSource,
            String targetDbName,
            org.jkiss.dbeaver.model.runtime.DBRProgressMonitor monitor) {
        if (targetDbName == null || targetDbName.isEmpty()) return null;
        if (!(dataSource instanceof org.jkiss.dbeaver.model.struct.DBSObjectContainer container)) {
            return null;
        }
        try {
            var children = container.getChildren(monitor);
            if (children == null) return null;
            for (var child : children) {
                if (child instanceof org.jkiss.dbeaver.model.struct.DBSInstance inst
                        && targetDbName.equalsIgnoreCase(child.getName())) {
                    return inst;
                }
            }
        } catch (Exception ignore) { }
        return null;
    }

    private static org.jkiss.dbeaver.model.DBPDataSourceContainer findContainer(String connName) {
        var project = org.jkiss.dbeaver.runtime.DBWorkbench.getPlatform().getWorkspace().getActiveProject();
        if (project == null) return null;
        for (var dsc : project.getDataSourceRegistry().getDataSources()) {
            if (connName.equals(dsc.getName())) return dsc;
        }
        return null;
    }

    private static org.jkiss.dbeaver.model.connection.DBPDriver findDriver(String searchTerm) {
        String term = searchTerm.toLowerCase();
        var reg = org.jkiss.dbeaver.registry.DataSourceProviderRegistry.getInstance();

        var byId = reg.findDriver(searchTerm);
        if (byId != null) return byId;

        for (var p : reg.getDataSourceProviders()) {
            for (var drv : p.getEnabledDrivers()) {
                if (drv.getId().toLowerCase().contains(term)
                        || drv.getName().toLowerCase().contains(term)) {
                    return drv;
                }
            }
        }
        return null;
    }

    // ── Wizard interaction (for tests that specifically test the wizard) ──

    /**
     * Opens the New Database Connection wizard from the menu.
     */
    public static SWTBotShell openNewConnectionWizard(SWTWorkbenchBot bot) {
        bot.menu("Database").menu("New Database Connection").click();
        bot.waitUntil(Conditions.shellIsActive(WIZARD_TITLE));
        return bot.shell(WIZARD_TITLE);
    }

    /**
     * Selects a driver in the connection wizard by typing its name in the filter,
     * then using reflection to select the first item in the custom AdvancedList canvas.
     */
    public static void selectDriver(SWTBot wizardBot, String driverName) {
        SWTBotText filterText = wizardBot.text();
        filterText.setFocus();
        filterText.setText(driverName);
        sleep(300); // wait for filter refresh

        // AdvancedList is a custom Canvas widget that SWTBot can't interact with.
        // Use reflection to select the first visible item.
        Display.getDefault().syncExec(() -> {
            var shell = ((org.eclipse.swt.widgets.Control) filterText.widget).getShell();
            var canvas = findAdvancedListCanvas(shell);
            if (canvas != null) {
                try {
                    var itemsField = canvas.getClass().getDeclaredField("items");
                    itemsField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    var items = (java.util.List<?>) itemsField.get(canvas);

                    // Reset selectedItem to null to avoid short-circuit in setSelection()
                    var selectedField = canvas.getClass().getDeclaredField("selectedItem");
                    selectedField.setAccessible(true);
                    selectedField.set(canvas, null);

                    if (!items.isEmpty()) {
                        for (var m : canvas.getClass().getDeclaredMethods()) {
                            if (m.getName().equals("setSelection") && m.getParameterCount() == 1) {
                                m.setAccessible(true);
                                m.invoke(canvas, items.get(0));
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
        });
        sleep(100);
    }

    private static org.eclipse.swt.widgets.Control findAdvancedListCanvas(
            org.eclipse.swt.widgets.Composite parent) {
        for (var child : parent.getChildren()) {
            String className = child.getClass().getName();
            if (className.contains("AdvancedList") && child.isVisible()) {
                return child;
            }
            if (child instanceof org.eclipse.swt.widgets.Composite composite) {
                var found = findAdvancedListCanvas(composite);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ── Navigator-based operations ──────────────────────────────────────

    /**
     * Opens the Edit Connection dialog for a named connection in the navigator.
     */
    public static SWTBotShell openEditConnection(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.select();
        connNode.contextMenu("Edit Connection").click();
        sleep(500);

        for (SWTBotShell shell : bot.shells()) {
            if (shell.getText().contains("configuration")) {
                shell.activate();
                return shell;
            }
        }
        throw new WidgetNotFoundException("Edit Connection dialog not found for: " + connectionName);
    }

    /**
     * Connects to a database by double-clicking the connection node.
     * Handles driver download if needed.
     */
    public static void connectTo(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.doubleClick();

        // Handle driver download dialog if it appears (should be pre-downloaded via ui.drivers.home)
        sleep(200);
        handleDriverDownload(bot);

        // Wait for connection to establish (children appear under the node)
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                try {
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
        }, 15000, 300);
    }

    /**
     * Handles driver download dialog if it appears.
     * The dialog title is "Driver settings" (contains "driver") and has a "Download" button.
     * Uses Conditions.shellIsActive-style polling for reliability.
     */
    public static void handleDriverDownload(SWTWorkbenchBot bot) {
        // Poll for up to 3 seconds for a shell containing "driver" in its title
        // (dialog only appears on first use when driver JARs aren't cached)
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            for (SWTBotShell shell : bot.shells()) {
                if (!shell.isOpen() || !shell.isVisible()) continue;
                String title = shell.getText();
                if (title.toLowerCase().contains("driver")) {
                    shell.activate();
                    SWTBot dlg = shell.bot();
                    try {
                        // The "Download" button is the Finish button in the WizardDialog
                        dlg.button("Download").click();
                        // Wait for download to complete and dialog to close
                        long dlDeadline = System.currentTimeMillis() + 60000;
                        while (shell.isOpen() && System.currentTimeMillis() < dlDeadline) {
                            sleep(500);
                        }
                        sleep(500);
                        return;
                    } catch (WidgetNotFoundException e) {
                        // Try other button labels
                        try { dlg.button("OK").click(); return; } catch (WidgetNotFoundException e2) { }
                        try { dlg.button("Cancel").click(); return; } catch (WidgetNotFoundException e3) { }
                    }
                }
            }
            sleep(300);
        }
    }

    /**
     * Reads a text field value by its label.
     */
    public static String getTextField(SWTBot bot, String label) {
        try {
            return bot.textWithLabel(label).getText();
        } catch (WidgetNotFoundException e) {
            String alt = label.endsWith(":") ? label.substring(0, label.length() - 1) : label + ":";
            return bot.textWithLabel(alt).getText();
        }
    }

    /**
     * Sets a text field value by its label.
     */
    public static void setTextField(SWTBot bot, String label, String value) {
        try {
            bot.textWithLabel(label).setText(value);
        } catch (WidgetNotFoundException e) {
            String alt = label.endsWith(":") ? label.substring(0, label.length() - 1) : label + ":";
            bot.textWithLabel(alt).setText(value);
        }
    }

    /**
     * Deletes a connection from the navigator.
     */
    public static void deleteConnection(SWTWorkbenchBot bot, String connectionName) {
        SWTBotTree tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
        SWTBotTreeItem connNode = NavigatorUtil.findConnectionNode(tree, connectionName);
        connNode.select();
        connNode.contextMenu("Delete").click();
        sleep(300);
        for (SWTBotShell shell : bot.shells()) {
            String title = shell.getText().toLowerCase();
            if (title.contains("confirm") || title.contains("delete")) {
                try { shell.bot().button("Yes").click(); return; } catch (WidgetNotFoundException e) { }
                try { shell.bot().button("OK").click(); return; } catch (WidgetNotFoundException e) { }
            }
        }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
