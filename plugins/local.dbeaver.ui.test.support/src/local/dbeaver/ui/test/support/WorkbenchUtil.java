package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class WorkbenchUtil {

    public static void configureDefaults() {
        SWTBotPreferences.TIMEOUT = 30000;
        SWTBotPreferences.PLAYBACK_DELAY = 50;
    }

    public static void closeWelcomePage(SWTWorkbenchBot bot) {
        try {
            bot.viewByTitle("Welcome").close();
        } catch (WidgetNotFoundException e) {
            // Welcome page not open, that's fine
        }
    }

    public static void closeTipOfTheDay(SWTWorkbenchBot bot) {
        try {
            bot.shell("Tip of the Day").close();
        } catch (WidgetNotFoundException e) {
            // No tip dialog
        }
        try {
            bot.shell("DBeaver Tip of the Day").close();
        } catch (WidgetNotFoundException e) {
            // No tip dialog
        }
    }

    public static void dismissInitialDialogs(SWTWorkbenchBot bot) {
        closeWelcomePage(bot);
        closeTipOfTheDay(bot);
        // Close any other unexpected dialogs
        try {
            bot.shell("License").close();
        } catch (WidgetNotFoundException e) {
            // ignore
        }
    }

    public static void closeAllEditors(SWTWorkbenchBot bot) {
        Display.getDefault().syncExec(() -> {
            IWorkbench wb = PlatformUI.getWorkbench();
            IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
            if (window != null && window.getActivePage() != null) {
                window.getActivePage().closeAllEditors(false);
            }
        });
    }

    public static void waitForJobs(SWTWorkbenchBot bot) {
        bot.waitUntil(new DefaultCondition() {
            @Override
            public boolean test() throws Exception {
                return org.eclipse.core.runtime.jobs.Job.getJobManager()
                        .find(null).length == 0;
            }

            @Override
            public String getFailureMessage() {
                return "Background jobs did not finish in time";
            }
        }, 60000, 500);
    }
}
