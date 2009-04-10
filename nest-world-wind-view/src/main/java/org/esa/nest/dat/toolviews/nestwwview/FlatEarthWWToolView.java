package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.view.FlatOrbitView;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.terrain.WMSBasicElevationModel;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.wms.Capabilities;
import gov.nasa.worldwind.wms.CapabilitiesRequest;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.examples.ClickAndGoSelectListener;
import gov.nasa.worldwind.examples.StatisticsPanel;
import gov.nasa.worldwind.examples.WMSLayersPanel;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.layers.Earth.OpenStreetMapLayer;
import gov.nasa.worldwind.layers.Earth.MSVirtualEarthLayer;
import gov.nasa.worldwind.layers.Earth.LandsatI3WMSLayer;
import gov.nasa.worldwind.layers.placename.PlaceNameLayer;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.util.Logging;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.visat.VisatApp;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

/**
 * The window displaying the world map.
 *
 */
public class FlatEarthWWToolView extends AbstractToolView {

    private final VisatApp datApp = VisatApp.getApp();
    private Dimension canvasSize = new Dimension(800, 600);

    private AppPanel wwjPanel = null;

    private final SurfaceImageLayer surfaceLayer = new SurfaceImageLayer();

    private final boolean includeStatusBar = true;

    public FlatEarthWWToolView() {
        Configuration.setValue(AVKey.GLOBE_CLASS_NAME, EarthFlat.class.getName());
        Configuration.setValue(AVKey.VIEW_CLASS_NAME, FlatOrbitView.class.getName());
    }

    @Override
    public JComponent createControl() {

        final Window windowPane = getPaneWindow();
        if(windowPane != null)
            windowPane.setSize(800,400);
        final JPanel mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.setSize(new Dimension(300, 300));

        // world wind canvas
        initialize(mainPane);

        final MSVirtualEarthLayer virtualEarthLayerA = new MSVirtualEarthLayer(MSVirtualEarthLayer.LAYER_AERIAL);
        virtualEarthLayerA.setName("MS Virtual Earth Aerial");
        insertTiledLayer(getWwd(), virtualEarthLayerA);

        surfaceLayer.setOpacity(0.8);
        surfaceLayer.setPickEnabled(false);
        surfaceLayer.setName("NEST Opened Products");
        insertTiledLayer(getWwd(), surfaceLayer);

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

    public static void insertTiledLayer(WorldWindow wwd, Layer layer) {
        int position = 0;
        final LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l instanceof PlaceNameLayer) {
                position = layers.indexOf(l);
                break;
            }
        }
        layers.add(position, layer);
    }

    private void initialize(JPanel mainPane) {
        // Create the WorldWindow.
        wwjPanel = new AppPanel(canvasSize, includeStatusBar);
        wwjPanel.setPreferredSize(canvasSize);

        // Put the pieces together.
        mainPane.add(wwjPanel, BorderLayout.CENTER);
    }

    public void setSelectedProduct(Product product) {
        if(surfaceLayer != null)
            surfaceLayer.setSelectedProduct(product);
    }

    public Product getSelectedProduct() {
        if(surfaceLayer != null)
            return surfaceLayer.getSelectedProduct();
        return null;
    }

    public void setProducts(Product[] products) {
        if(surfaceLayer != null) {
            for (Product prod : products) {
                try {
                    surfaceLayer.addProduct(prod);
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
        if(surfaceLayer != null)
            surfaceLayer.removeProduct(product);
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
            this.wwd.addSelectListener(new ClickAndGoSelectListener(this.getWwd(), WorldMapLayer.class));

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