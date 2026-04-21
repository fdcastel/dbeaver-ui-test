package local.dbeaver.postgresql.ui.test;

import static org.junit.Assert.*;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import local.dbeaver.ui.test.support.ConnectionUtil;
import local.dbeaver.ui.test.support.NavigatorUtil;
import local.dbeaver.ui.test.support.ScreenshotUtil;

/**
 * L1.1 (PostgreSQL): Connection creation + navigator visibility.
 *
 * Uses the fast API path (no wizard UI). Analogous to the Firebird variant.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PostgreSQLConnectionTest {

    private static SWTWorkbenchBot bot;

    @BeforeClass
    public static void setUp() {
        bot = PostgreSQLTestContext.getBot();
    }

    @Test
    public void test01_createPostgreSQLConnection() {
        String connName = ConnectionUtil.createPostgreSQLConnectionViaAPI(
                PostgreSQLTestContext.getHost(),
                PostgreSQLTestContext.getPort(),
                PostgreSQLTestContext.getDatabase(),
                PostgreSQLTestContext.getUser(),
                PostgreSQLTestContext.getPassword());

        assertNotNull("Connection name should be returned", connName);
        PostgreSQLTestContext.setConnectionName(connName);
    }

    @Test
    public void test02_connectionAppearsInNavigator() {
        String connName = PostgreSQLTestContext.getConnectionName();
        assertNotNull("Connection should have been created in test01", connName);

        try {
            var tree = NavigatorUtil.getDatabaseNavigatorTree(bot);
            var connNode = NavigatorUtil.findConnectionNode(tree, connName);
            assertNotNull("Connection should be visible in navigator", connNode);
        } catch (Exception e) {
            ScreenshotUtil.captureOnFailure("PostgreSQLConnectionTest", "test02");
            fail("Connection '" + connName + "' not found in navigator: " + e.getMessage());
        }
    }
}
