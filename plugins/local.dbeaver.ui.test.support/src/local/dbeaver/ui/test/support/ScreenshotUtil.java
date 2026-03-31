package local.dbeaver.ui.test.support;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.SWT;

import java.io.File;

public class ScreenshotUtil {

    private static final String ARTIFACTS_DIR = System.getProperty(
            "uitest.artifacts.dir",
            System.getProperty("user.dir") + "/artifacts");

    /**
     * Captures a screenshot and saves it to the artifacts directory.
     */
    public static void capture(String name) {
        File dir = new File(ARTIFACTS_DIR);
        dir.mkdirs();
        String fileName = name.replaceAll("[^a-zA-Z0-9_-]", "_") + ".png";
        File file = new File(dir, fileName);

        Display.getDefault().syncExec(() -> {
            Display display = Display.getDefault();
            Image image = new Image(display,
                    display.getBounds().width, display.getBounds().height);
            GC gc = new GC(display);
            gc.copyArea(image, 0, 0);
            gc.dispose();

            ImageLoader loader = new ImageLoader();
            loader.data = new ImageData[] { image.getImageData() };
            loader.save(file.getAbsolutePath(), SWT.IMAGE_PNG);
            image.dispose();
        });

        System.out.println("Screenshot saved: " + file.getAbsolutePath());
    }

    /**
     * Captures a screenshot on test failure.
     */
    public static void captureOnFailure(String testClass, String testMethod) {
        capture("FAIL_" + testClass + "_" + testMethod);
    }
}
