package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

public class NavigatorUtil {

    /**
     * Returns the Database Navigator tree widget.
     */
    public static SWTBotTree getDatabaseNavigatorTree(SWTWorkbenchBot bot) {
        try {
            return bot.viewByTitle("Database Navigator").bot().tree();
        } catch (WidgetNotFoundException e) {
            // Try the view by ID
            return bot.viewById("org.jkiss.dbeaver.core.databaseNavigator").bot().tree();
        }
    }

    /**
     * Finds a connection node in the navigator tree by partial name match.
     */
    public static SWTBotTreeItem findConnectionNode(SWTBotTree tree, String connectionName) {
        SWTBotTreeItem[] items = tree.getAllItems();
        for (SWTBotTreeItem item : items) {
            if (item.getText().contains(connectionName)) {
                return item;
            }
        }
        throw new WidgetNotFoundException("Connection '" + connectionName + "' not found in navigator");
    }

    /**
     * Expands a path in the navigator tree.
     * @param tree the navigator tree
     * @param path each segment of the tree path to expand
     * @return the final tree item at the end of the path
     */
    public static SWTBotTreeItem expandPath(SWTBotTree tree, String... path) {
        SWTBotTreeItem current = null;
        for (int i = 0; i < path.length; i++) {
            if (i == 0) {
                current = findItemByPartialText(tree.getAllItems(), path[i]);
            } else {
                current.expand();
                // Wait for children to load
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                current = findItemByPartialText(current.getItems(), path[i]);
            }
        }
        return current;
    }

    /**
     * Refreshes a node in the navigator by right-clicking and selecting Refresh.
     */
    public static void refreshNode(SWTWorkbenchBot bot, SWTBotTreeItem node) {
        node.select();
        node.contextMenu("Refresh").click();
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static SWTBotTreeItem findItemByPartialText(SWTBotTreeItem[] items, String text) {
        for (SWTBotTreeItem item : items) {
            if (item.getText().contains(text)) {
                return item;
            }
        }
        throw new WidgetNotFoundException("Tree item containing '" + text + "' not found");
    }
}
