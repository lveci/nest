package org.esa.nest.dat;

import com.jidesoft.swing.JideScrollPane;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.product.ProductTree;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.util.Debug;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import java.awt.Dimension;
import java.beans.PropertyVetoException;

/**
 * The tool window which displays the tree of open products.
 */
public class DatProductsToolView extends AbstractToolView {

    public static final String ID = DatProductsToolView.class.getName();

    /**
     * DAT's product tree
     */
    private ProductTree productTree;
    private DatApp datApp;

    public DatProductsToolView() {
        this.datApp = DatApp.getApp();
        // We need product tree early, otherwise VISAT cannot add ProductTreeListeners
        initProductTree();
    }

    public ProductTree getProductTree() {
        return productTree;
    }

    @Override
    public JComponent createControl() {
        final JScrollPane productTreeScrollPane = new JideScrollPane(productTree); // <JIDE>
        productTreeScrollPane.setPreferredSize(new Dimension(320, 480));
        productTreeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        productTreeScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        productTreeScrollPane.setBorder(null);
        productTreeScrollPane.setViewportBorder(null);

        return productTreeScrollPane;
    }

    private void initProductTree() {
        productTree = new ProductTree();
        productTree.setExceptionHandler(new org.esa.beam.framework.ui.ExceptionHandler() {

            public boolean handleException(final Exception e) {
                datApp.showErrorDialog(e.getMessage());
                return true;
            }
        });
        productTree.addProductTreeListener(new VisatPTL());
        productTree.setCommandManager(datApp.getCommandManager());
        productTree.setCommandUIFactory(datApp.getCommandUIFactory());
        DatApp.getApp().getProductManager().addListener(new ProductManager.Listener() {
            public void productAdded(final ProductManager.Event event) {
                productTree.addProduct(event.getProduct());
                DatApp.getApp().getPage().showToolView(ID);
            }

            public void productRemoved(final ProductManager.Event event) {
                final Product product = event.getProduct();
                productTree.removeProduct(product);
                if (DatApp.getApp().getSelectedProduct() == product) {
                    DatApp.getApp().setSelectedProductNode((ProductNode) null);
                }
            }
        });

    }


    /**
     * This listener listens to product tree events in VISAT's product browser.
     */
    private class VisatPTL implements ProductTreeListener {

        public VisatPTL() {
        }

        public void productAdded(final Product product) {
            Debug.trace("DatApp: product added: " + product.getDisplayName());
            datApp.setSelectedProductNode(product);
        }

        public void productRemoved(final Product product) {
            Debug.trace("DatApp: product removed: " + product.getDisplayName());
            if (datApp.getSelectedProduct() != null && datApp.getSelectedProduct() == product) {
                datApp.setSelectedProductNode((ProductNode) null);
            } else {
                datApp.updateState();
            }
        }

        public void productSelected(final Product product, final int clickCount) {
            datApp.setSelectedProductNode(product);
        }

        public void tiePointGridSelected(final TiePointGrid tiePointGrid, final int clickCount) {
            rasterDataNodeSelected(tiePointGrid, clickCount);
        }

        public void bandSelected(final Band band, final int clickCount) {
            rasterDataNodeSelected(band, clickCount);
        }

        private void rasterDataNodeSelected(final RasterDataNode raster, final int clickCount) {
            datApp.setSelectedProductNode(raster);
            final JInternalFrame[] internalFrames = datApp.findInternalFrames(raster);
            JInternalFrame frame = null;
            for (final JInternalFrame internalFrame : internalFrames) {
                final int numRasters = ((ProductSceneView) internalFrame.getContentPane()).getNumRasters();
                if (numRasters == 1) {
                    frame = internalFrame;
                    break;
                }
            }
            if (frame != null) {
                try {
                    frame.setSelected(true);
                } catch (PropertyVetoException e) {
                    // ok
                }
            } else if (clickCount == 2) {
                final ExecCommand command = datApp.getCommandManager().getExecCommand("showImageView");
                command.execute(clickCount);
            }
        }

        public void metadataElementSelected(final MetadataElement group, final int clickCount) {
            datApp.setSelectedProductNode(group);
            final JInternalFrame frame = datApp.findInternalFrame(group);
            if (frame != null) {
                try {
                    frame.setSelected(true);
                } catch (PropertyVetoException e) {
                    // ok
                }
                return;
            }
            if (clickCount == 2) {
                datApp.createProductMetadataView(group);
            }
        }
    }
}
