package local.dbeaver.postgresql.ui.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Aggregate suite for all PostgreSQL UI tests. Order matters: connection setup
 * must run first so subsequent tests can reuse the configured connection.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        PostgreSQLConnectionTest.class,
        ScriptCommitRollbackTest.class,
        ScriptCommitManualModeTest.class,
})
public class AllPostgreSQLUITests {
}
