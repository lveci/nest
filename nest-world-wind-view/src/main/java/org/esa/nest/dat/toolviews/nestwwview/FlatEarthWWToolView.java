package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.view.FlatOrbitView;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.examples.ClickAndGoSelectListener;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.layers.Earth.MSVirtualEarthLayer;
import gov.nasa.worldwind.layers.Earth.LandsatI3WMSLayer;
import gov.nasa.worldwind.util.StatusBar;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;

/**
 * The window displaying the world map.
 *
 */
public class FlatEarthWWToolView extends AbstractToolView {

    private final VisatApp datApp = VisatApp.getApp();
    private Dimension canvasSize = new Dimension(800, 600);

    private AppPanel wwjPanel = null;

    private final ProductLayer productLayer = new ProductLayer();

    private static final boolean includeStatusBar = true;

    public FlatEarthWWToolView() {
        Configuration.setValue(AVKey.GLOBE_CLASS_NAME, EarthFlat.class.getName());
        Configuration.setValue(AVKey.VIEW_CLASS_NAME, FlatOrbitView.class.getName());
    }

    @Override
    public JComponent createControl() {

        final Window windowPane = getPaneWindow();
        if(windowPane != null)
            windowPane.setSize(300,300);
        final JPanel mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.setSize(new Dimension(300, 300));

        // world wind canvas
        initialize(mainPane);
        final LayerList layerList = getWwd().getModel().getLayers();

        final MSVirtualEarthLayer virtualEarthLayerA = new MSVirtualEarthLayer(MSVirtualEarthLayer.LAYER_AERIAL);
        virtualEarthLayerA.setName("MS Virtual Earth Aerial");
        layerList.add(virtualEarthLayerA);

        productLayer.setOpacity(1.0);
        productLayer.setPickEnabled(false);
        productLayer.setName("NEST Opened Products");
        layerList.add(productLayer);

        final Layer placeNameLayer = layerList.getLayerByName("Place Names");
        placeNameLayer.setEnabled(false);

        // Add an internal frame listener to VISAT so that we can update our
        // world map window with the information of the currently activated  product scene view.
        datApp.addInternalFrameListener(new FlatEarthWWToolView.WWIFL());
        datApp.addProductTreeListener(new FlatEarthWWToolView.WWPTL());
        setProducts(datApp.getProductManager().getProducts());
        setSelectedProduct(datApp.getSelectedProduct());

        return mainPane;
    }

    public WorldWindowGLCanvas getWwd() {
        return wwjPanel.getWwd();
    }

    private void initialize(JPanel mainPane) {
        // Create the WorldWindow.
        wwjPanel = new AppPanel(canvasSize, includeStatusBar);
        wwjPanel.setPreferredSize(canvasSize);

        // Put the pieces together.
        mainPane.add(wwjPanel, BorderLayout.CENTER);
    }

    public void setSelectedProduct(Product product) {
        if(productLayer != null)
            productLayer.setSelectedProduct(product);
        wwjPanel.repaint();
    }

    public Product getSelectedProduct() {
        if(productLayer != null)
            return productLayer.getSelectedProduct();
        return null;
    }

    public void setProducts(Product[] products) {
        if(productLayer != null) {
            for (Product prod : products) {
                try {
                    productLayer.addProduct(prod);
                } catch(Exception e) {
                    datApp.showErrorDialog("WorldWind unable to add product " + prod.getName()+
                                            "\n"+e.getMessage());
                }
            }
        }
    }

    public void removeProduct(Product product) {
        if(getSelectedProduct() == product)
            setSelectedProduct(null);
        if(productLayer != null)
            productLayer.removeProduct(product);
    }

    public static class AppPanel extends JPanel {
        private final WorldWindowGLCanvas wwd;
        private StatusBar statusBar = null;

        public AppPanel(Dimension canvasSize, boolean includeStatusBar) {
            super(new BorderLayout());

            this.wwd = new WorldWindowGLCanvas();
            this.wwd.setPreferredSize(canvasSize);

            // Create the default model as described in the current worldwind properties.
            final Model m = (Model) WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME);
            this.wwd.setModel(m);

            final LayerList layerList = m.getLayers();
            for(Layer layer : layerList) {
                if(layer instanceof CompassLayer || layer instanceof WorldMapLayer ||
                   layer instanceof LandsatI3WMSLayer || layer instanceof SkyGradientLayer)
                    layerList.remove(layer);
            }


            // Setup a select listener for the worldmap click-and-go feature
            this.wwd.addSelectListener(new ClickAndGoSelectListener(wwd, WorldMapLayer.class));

            this.add(this.wwd, BorderLayout.CENTER);
            if (includeStatusBar) {
                this.statusBar = new MinimalStatusBar();
                this.add(statusBar, BorderLayout.PAGE_END);
                this.statusBar.setEventSource(wwd);
            }
        }

        public final WorldWindowGLCanvas getWwd() {
            return wwd;
        }

        public final StatusBar getStatusBar() {
            return statusBar;
        }
    }

    private class WWPTL implements ProductTreeListener {

        public WWPTL() {
        }

        public void productAdded(final Product product) {
            setSelectedProduct(product);
            setProducts(datApp.getProductManager().getProducts());
        }

        public void productRemoved(final Product product) {
            removeProduct(product);
        }

        public void productSelected(final Product product, final int clickCount) {
            setSelectedProduct(product);
        }

        public void metadataElementSelected(final MetadataElement group, final int clickCount) {
            setSelectedProduct(group.getProduct());
        }

        public void tiePointGridSelected(final TiePointGrid tiePointGrid, final int clickCount) {
            setSelectedProduct(tiePointGrid.getProduct());
        }

        public void bandSelected(final Band band, final int clickCount) {
            setSelectedProduct(band.getProduct());
        }
    }

    private class WWIFL extends InternalFrameAdapter {

        @Override
        public void internalFrameActivated(final InternalFrameEvent e) {
            final Container contentPane = e.getInternalFrame().getContentPane();
            Product product = null;
            if (contentPane instanceof ProductSceneView) {
                product = ((ProductSceneView) contentPane).getProduct();
            }
            setSelectedProduct(product);
        }

        @Override
        public void internalFrameDeactivated(final InternalFrameEvent e) {
        }
    }
}