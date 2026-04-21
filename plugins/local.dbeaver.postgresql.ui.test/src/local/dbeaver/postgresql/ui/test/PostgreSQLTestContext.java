package local.dbeaver.postgresql.ui.test;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import local.dbeaver.ui.test.support.WorkbenchUtil;

/**
 * Shared state across all PostgreSQL UI tests.
 * Initialized once by the suite runner.
 */
public class PostgreSQLTestContext {
    private static SWTWorkbenchBot bot;
    private static String connectionName;
    private static boolean workbenchReady;

    public static synchronized SWTWorkbenchBot getBot() {
        if (bot == null) {
            bot = new SWTWorkbenchBot();
            WorkbenchUtil.configureDefaults();
        }
        if (!workbenchReady) {
            WorkbenchUtil.waitForWorkbench(bot);
            WorkbenchUtil.configureDBeaver();
            WorkbenchUtil.dismissInitialDialogs(bot);
            workbenchReady = true;
        }
        return bot;
    }

    public static String getHost()     { return System.getProperty("postgres.host", "localhost"); }
    public static String getPort()     { return System.getProperty("postgres.port", "5432"); }
    public static String getDatabase() { return System.getProperty("postgres.database", "ui_test"); }
    public static String getUser()     { return System.getProperty("postgres.user", "postgres"); }
    public static String getPassword() { return System.getProperty("postgres.password", "postgres"); }

    public static String getConnectionName() { return connectionName; }
    public static void setConnectionName(String name) { connectionName = name; }
    public static boolean hasConnection() { return connectionName != null; }
}
