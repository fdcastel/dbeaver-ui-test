package local.dbeaver.ui.test.support;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import java.io.File;

public class ScreenshotUtil {

    private static final String ARTIFACTS_DIR = System.getProperty(
            "uitest.artifacts.dir",
            System.getProperty("user.dir") + "/artifacts");

    /**
     * Captures the active DBeaver workbench window and saves it as a PNG under
     * the artifacts directory. Uses {@code Control.print(GC)} so we render the
     * widget tree directly — we get a clean DBeaver image even if the runner's
     * log window or some other process is covering the desktop underneath.
     */
    public static void capture(String name) {
        File dir = new File(ARTIFACTS_DIR);
        dir.mkdirs();
        String fileName = name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".png";
        File file = new File(dir, fileName);

        Display.getDefault().syncExec(() -> {
            try {
                Shell shell = findTargetShell();
                if (shell == null) {
                    System.err.println("ScreenshotUtil: no shell found for '" + name + "'");
                    return;
                }
                Rectangle bounds = shell.getBounds();
                Image image = new Image(shell.getDisplay(), bounds.width, bounds.height);
                GC gc = new GC(image);
                try {
                    // print() renders the widget tree into the GC without needing
                    // the shell to be in foreground — perfect for CI where other
                    // processes (the GitHub Actions runner shell, dialogs, etc.)
                    // may be covering parts of the display.
                    shell.print(gc);
                } finally {
                    gc.dispose();
                }

                ImageLoader loader = new ImageLoader();
                loader.data = new ImageData[] { image.getImageData() };
                loader.save(file.getAbsolutePath(), SWT.IMAGE_PNG);
                image.dispose();
            } catch (Exception e) {
                System.err.println("ScreenshotUtil.capture failed for '" + name + "': " + e);
            }
        });

        System.out.println("Screenshot saved: " + file.getAbsolutePath());
    }

    public static void captureOnFailure(String testClass, String testMethod) {
        capture("FAIL_" + testClass + "_" + testMethod);
    }

    /**
     * Picks the shell to snapshot. Prefers any visible modal dialog (so if an
     * "Execution Error" popup is up we capture that, not the DBeaver main
     * window behind it); falls back to the active workbench window.
     */
    private static Shell findTargetShell() {
        Display display = Display.getDefault();

        Shell workbench = null;
        try {
            IWorkbench wb = PlatformUI.getWorkbench();
            if (wb != null && wb.getActiveWorkbenchWindow() != null) {
                workbench = wb.getActiveWorkbenchWindow().getShell();
            }
        } catch (Exception ignore) { /* fall through */ }

        // Prefer any visible shell that's NOT the workbench — that's almost
        // always a dialog/popup on top, which is what a post-hoc viewer cares
        // about most.
        for (Shell s : display.getShells()) {
            if (s == null || s.isDisposed() || !s.isVisible()) continue;
            if (s == workbench) continue;
            return s;
        }

        if (workbench != null && !workbench.isDisposed() && workbench.isVisible()) {
            return workbench;
        }
        Shell active = display.getActiveShell();
        if (active != null && !active.isDisposed() && active.isVisible()) return active;
        for (Shell s : display.getShells()) {
            if (!s.isDisposed() && s.isVisible()) return s;
        }
        return null;
    }
}
