package local.dbeaver.firebird.ui.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Main test suite for Firebird UI tests.
 * Runs all test classes in order: connection setup first,
 * then scenarios that depend on having a connection.
 */
@RunWith(Suite.class)
@SuiteClasses({
    FirebirdConnectionWizardTest.class,
    EditConnectionRoundTripTest.class,
    SQLEditorSetTermTest.class,
    ExecuteBlockParameterBindingTest.class,
    ExecuteBlockNamedParamsTest.class,
    NavigatorSmokeTest.class
})
public class AllFirebirdUITests {
}
