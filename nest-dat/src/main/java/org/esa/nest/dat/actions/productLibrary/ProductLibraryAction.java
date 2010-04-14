package org.esa.nest.dat.actions.productLibrary;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.actions.productLibrary.ui.ProductLibraryUI;

import javax.swing.*;
import java.io.File;
import java.io.IOException;


/**
 * opens a dialog to preview and open products.
 */
public class ProductLibraryAction extends ExecCommand {

    private static final String ID = "productLibrary";
    private static final String HELP_ID = ID;

    private static final ProductLibraryAction instance = new ProductLibraryAction();
    private ProductLibraryUI productLibraryUI = null;

    public ProductLibraryAction() {
        super(ID);
    }

    /**
     * Retrieves the instance of this class.
     *
     * @return the single instance.
     */
    public static ProductLibraryAction getInstance() {
        return instance;
    }

    public void ShowProductLibrary() {
        actionPerformed(null);
    }

    public synchronized ProductLibraryUI getProductLibraryUI() {
        if (productLibraryUI == null) {
            final VisatApp visatApp = VisatApp.getApp();
            productLibraryUI = new ProductLibraryUI(visatApp, HELP_ID);
            productLibraryUI.setProductOpenHandler(new MyProductOpenHandler(visatApp));
            productLibraryUI.getFrame().setIconImage(visatApp.getMainFrame().getIconImage());
        }
        return productLibraryUI;
    }

    @Override
    public void actionPerformed(final CommandEvent event) {
        getProductLibraryUI().getFrame().setVisible(true);
    }

    @Override
    public void updateState(final CommandEvent event) {
    }

    /**
     * Tells a plug-in to update its component tree (if any) since the Java look-and-feel has changed.
     * <p/>
     * <p>If a plug-in uses top-level containers such as dialogs or frames, implementors of this method should invoke
     * <code>SwingUtilities.updateComponentTreeUI()</code> on such containers.
     */
    @Override
    public void updateComponentTreeUI() {
        if (productLibraryUI != null) {
            SwingUtilities.updateComponentTreeUI(productLibraryUI.getFrame());
        }
    }

    private static class MyProductOpenHandler implements ProductLibraryUI.ProductOpenHandler {

        private final VisatApp visatApp;

        public MyProductOpenHandler(final VisatApp visatApp) {
            this.visatApp = visatApp;
        }

        public void openProducts(final File[] productFiles) {
            for (File productFile : productFiles) {
                if (isProductOpen(productFile)) {
                    continue;
                }
                try {
                    final Product product = ProductIO.readProduct(productFile, null);

                    final ProductManager productManager = visatApp.getProductManager();
                    productManager.addProduct(product);
                } catch (IOException e) {
                    visatApp.showErrorDialog("Not able to open product:\n" +
                            productFile.getPath());
                }
            }
        }

        private boolean isProductOpen(final File productFile) {
            final Product openedProduct = visatApp.getOpenProduct(productFile);
            if (openedProduct != null) {
                visatApp.showInfoDialog("Product '" + openedProduct.getName() + "' is already opened.", null);
                return true;
            }
            return false;
        }
    }
}