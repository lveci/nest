
package org.esa.nest.dat;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.runtime.RuntimeRunnable;
import com.jidesoft.utils.Lm;
import com.jidesoft.utils.SystemInfo;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.util.Debug;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * The startup class for DAT. It provides the <code>main</code> method for the application.
 * <p/>
 * <p>The DAT application accepts the following command line options: <ld> <li> <code>-d</code> or
 * <code>--debug</code> sets DAT into debugging mode <li> <code>-l <i>file</i></code> or <code>--logfile
 * <i>file</i></code> sets the logfile for DAT to <i>file</i> </ld>
 *
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2007-12-17 21:22:54 $
 */
public class DatMain implements RuntimeRunnable {
    /**
     * Entry point for the DAT application called by the Ceres runtime.
     *
     * @param argument        a {@code String[]} containing the command line arguments
     * @param progressMonitor a progress monitor
     * @throws Exception if an error occurs
     */
    public void run(Object argument, ProgressMonitor progressMonitor) throws Exception {

        String[] args = new String[0];
        if (argument instanceof String[]) {
            args = (String[]) argument;
        }

        Locale.setDefault(Locale.ENGLISH); // Force usage of english locale

        Lm.verifyLicense("Brockmann Consult", "BEAM", "lCzfhklpZ9ryjomwWxfdupxIcuIoCxg2");
        // set special properties for Mac OS X
        if (SystemInfo.isMacOSX()) {
            if (System.getProperty("apple.laf.useScreenMenuBar") == null) {
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            }
            if (System.getProperty("apple.awt.brushMetalLook") == null) {
                System.setProperty("apple.awt.brushMetalLook", "true");
            }
        }

        boolean debugEnabled = true; 
        ArrayList<String> productFilepathList = new ArrayList<String>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (arg.equals("-d") || arg.equals("--debug")) {
                    debugEnabled = true;
                } else {
                    System.err.println("DAT error: illegal option '" + arg + "'");
                    return;
                }
            } else {
                productFilepathList.add(arg);
            }
        }

        Debug.setEnabled(debugEnabled);

        DatApp.start(progressMonitor);
        openProducts(productFilepathList);
    }

    private static void openProducts(ArrayList<String> productFilepathList) {
        for (String productFilepath : productFilepathList) {
            openProduct(productFilepath);
        }
    }

    private static void openProduct(final String productFilepath) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                UIUtils.setRootFrameWaitCursor(DatApp.getApp().getMainFrame());
                try {
                    openProductImpl(productFilepath);
                } finally {
                    UIUtils.setRootFrameDefaultCursor(DatApp.getApp().getMainFrame());
                }
            }
        });
    }

    private static void openProductImpl(final String productFilepath) {
        final File productFile = new File(productFilepath);
        final Product product;
        try {
            product = ProductIO.readProduct(productFile, null);
            if (product == null) {
                final MessageFormat mf = new MessageFormat("No reader found for data product\n'{0}'."); /*I18N*/
                final Object[] args = new Object[]{productFile.getPath()};
                showError(mf.format(args));
                return;
            }
        } catch (IOException e) {
            final MessageFormat mf = new MessageFormat("I/O error while opening file\n{0}:\n{1}"); /*I18N*/
            final Object[] args = new Object[]{productFile.getPath(), e.getMessage()};
            showError(mf.format(args));
            return;
        }
        DatApp.getApp().addProduct(product);
    }

    private static void showError(final String message) {
        JOptionPane.showMessageDialog(null,
                                      message,
                                      "DAT", /*I18N*/
                                      JOptionPane.ERROR_MESSAGE);
    }
}
