package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwind.terrain.WMSBasicElevationModel;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.wms.Capabilities;
import gov.nasa.worldwind.wms.CapabilitiesRequest;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.examples.ClickAndGoSelectListener;
import gov.nasa.worldwind.examples.StatisticsPanel;
import gov.nasa.worldwind.examples.WMSLayersPanel;
import gov.nasa.worldwind.layers.CompassLayer;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.WorldMapLayer;
import gov.nasa.worldwind.layers.Earth.OpenStreetMapLayer;
import gov.nasa.worldwind.layers.Earth.MSVirtualEarthLayer;
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
 * @version $Revision: 1.5 $ $Date: 2009-04-03 19:59:29 $
 */
public class NestWWToolView extends AbstractToolView {

    //private static final String loadDEMCommand = "loadDEM";
    //private static final ImageIcon loadDEMIcon = DatUtils.LoadIcon("org/esa/nest/icons/dem24.gif");

    private final VisatApp datApp = VisatApp.getApp();
    private Dimension canvasSize = new Dimension(800, 600);

    private AppPanel wwjPanel = null;
    private LayerPanel layerPanel = null;
    private StatisticsPanel statsPanel = null;

    private JSlider opacitySlider = null;
    private SurfaceImageLayer surfaceLayer = null;

    private final Dimension wmsPanelSize = new Dimension(400, 600);

    private JTabbedPane tabbedPane = new JTabbedPane();
    private int previousTabIndex;

    private static final String[] servers = new String[]
        {
            "http://neowms.sci.gsfc.nasa.gov/wms/wms",
            //"http://mapserver.flightgear.org/cgi-bin/landcover",
            "http://wms.jpl.nasa.gov/wms.cgi",
            "http://worldwind46.arc.nasa.gov:8087/wms"
        };

    public NestWWToolView() {
    }

    @Override
    public JComponent createControl() {

        final JPanel mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.setSize(new Dimension(300, 300));

        /*     JToolBar toolbar = new JToolBar();

          JButton loadDEMButton = new JButton();
          loadDEMButton.setName(getClass().getName() + loadDEMCommand);

          loadDEMButton = (JButton) ToolButtonFactory.createButton(loadDEMIcon, false);
          loadDEMButton.setBackground(mainPane.getBackground());
          loadDEMButton.setActionCommand(loadDEMCommand);
          loadDEMButton.setVisible(true);

          loadDEMButton.addActionListener(new ActionListener() {

              public void actionPerformed(final ActionEvent e) {
                  LoadDEM();
              }
          });
          toolbar.add(loadDEMButton);

          mainPane.add(toolbar, BorderLayout.NORTH); */

        // world wind canvas
        initialize(mainPane, true, false, false);

        final MSVirtualEarthLayer virtualEarthLayerA = new MSVirtualEarthLayer(MSVirtualEarthLayer.LAYER_AERIAL);
        virtualEarthLayerA.setName("MS Virtual Earth Aerial");
        insertTiledLayer(getWwd(), virtualEarthLayerA);

        final MSVirtualEarthLayer virtualEarthLayerR = new MSVirtualEarthLayer(MSVirtualEarthLayer.LAYER_ROADS);
        virtualEarthLayerR.setName("MS Virtual Earth Roads");
        virtualEarthLayerR.setEnabled(false);
        insertTiledLayer(getWwd(), virtualEarthLayerR);

        final MSVirtualEarthLayer virtualEarthLayerH = new MSVirtualEarthLayer(MSVirtualEarthLayer.LAYER_HYBRID);
        virtualEarthLayerH.setName("MS Virtual Earth Hybrid");
        virtualEarthLayerH.setEnabled(false);
        insertTiledLayer(getWwd(), virtualEarthLayerH);

        final OpenStreetMapLayer streetLayer = new OpenStreetMapLayer();
        streetLayer.setOpacity(0.7);
        streetLayer.setEnabled(false);
        streetLayer.setName("Open Street Map");
        insertTiledLayer(getWwd(), streetLayer);

        surfaceLayer = new SurfaceImageLayer();
        surfaceLayer.setOpacity(0.8);
        surfaceLayer.setPickEnabled(false);
        surfaceLayer.setName("NEST Opened Products");
        insertTiledLayer(getWwd(), surfaceLayer);

        // Add an internal frame listener to VISAT so that we can update our
        // world map window with the information of the currently activated  product scene view.
        datApp.addInternalFrameListener(new NestWWToolView.WWIFL());
        datApp.addProductTreeListener(new NestWWToolView.WWPTL());
        setProducts(datApp.getProductManager().getProducts());
        setSelectedProduct(datApp.getSelectedProduct());

        return mainPane;
    }

    public WorldWindowGLCanvas getWwd() {
        return wwjPanel.getWwd();
    }

    public static void insertTiledLayer(WorldWindow wwd, Layer layer) {
        int compassPosition = 0;
        final LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l instanceof PlaceNameLayer) {
                compassPosition = layers.indexOf(l);
                break;
            }
        }
        layers.add(compassPosition, layer);
    }

    public static void insertBeforeCompass(WorldWindow wwd, Layer layer) {
        // Insert the surfaceLayer into the surfaceLayer list just before the compass.
        int compassPosition = 0;
        final LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers) {
            if (l instanceof CompassLayer) {
                compassPosition = layers.indexOf(l);
                break;
            }
        }
        layers.add(compassPosition, layer);
    }

    private void initialize(JPanel mainPane, boolean includeStatusBar, boolean includeLayerPanel, boolean includeStatsPanel) {
        // Create the WorldWindow.
        wwjPanel = new AppPanel(canvasSize, includeStatusBar);
        wwjPanel.setPreferredSize(canvasSize);

        // Put the pieces together.
        mainPane.add(wwjPanel, BorderLayout.CENTER);
        if (includeLayerPanel) {
            layerPanel = new LayerPanel(wwjPanel.getWwd(), null);
            mainPane.add(layerPanel, BorderLayout.WEST);

            layerPanel.add(makeControlPanel(), BorderLayout.SOUTH);
            layerPanel.update(getWwd());
        }
        if (includeStatsPanel) {
            statsPanel = new StatisticsPanel(wwjPanel.getWwd(), new Dimension(250, canvasSize.height));
            mainPane.add(statsPanel, BorderLayout.EAST);
            wwjPanel.getWwd().addRenderingListener(new RenderingListener() {
                public void stageChanged(RenderingEvent event) {
                    if (event.getSource() instanceof WorldWindow) {
                        EventQueue.invokeLater(new Runnable() {
                            public void run() {
                                statsPanel.update(wwjPanel.getWwd());
                            }
                        });
                    }
                }
            });
        }

        boolean addWMSPanel = false;           
        if(addWMSPanel) {
            tabbedPane.add(new JPanel());
            tabbedPane.setTitleAt(0, "+");
            tabbedPane.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent changeEvent) {
                    if (tabbedPane.getSelectedIndex() != 0) {
                        previousTabIndex = tabbedPane.getSelectedIndex();
                        return;
                    }

                    final String server = JOptionPane.showInputDialog("Enter wms server URL");
                    if (server == null || server.length() < 1) {
                        tabbedPane.setSelectedIndex(previousTabIndex);
                        return;
                    }

                    // Respond by adding a new WMSLayerPanel to the tabbed pane.
                    if (addTab(tabbedPane.getTabCount(), server.trim()) != null)
                        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
                }
            });

            // Create a tab for each server and add it to the tabbed panel.
            for (int i = 0; i < servers.length; i++) {
                this.addTab(i + 1, servers[i]); // i+1 to place all server tabs to the right of the Add Server tab
            }

            // Display the first server pane by default.
            this.tabbedPane.setSelectedIndex(this.tabbedPane.getTabCount() > 0 ? 1 : 0);
            this.previousTabIndex = this.tabbedPane.getSelectedIndex();

            mainPane.add(tabbedPane, BorderLayout.EAST);
        }
    }

    private JPanel makeControlPanel() {
        final JPanel controlPanel = new JPanel(new GridLayout(0, 1, 5, 5));

        opacitySlider = new JSlider();
        opacitySlider.setMaximum(100);
        opacitySlider.setValue((int) (surfaceLayer.getOpacity() * 100));
        opacitySlider.setEnabled(true);
        opacitySlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int value = opacitySlider.getValue();
                surfaceLayer.setOpacity(value / 100d);
                getWwd().repaint();
            }
        });
        final JPanel opacityPanel = new JPanel(new BorderLayout(5, 5));
        opacityPanel.add(new JLabel("Opacity"), BorderLayout.WEST);
        opacityPanel.add(this.opacitySlider, BorderLayout.CENTER);

        controlPanel.add(opacityPanel);
        controlPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        return controlPanel;
    }

    private void LoadDEM() {

        //_eventListener.LoadDEM();
    }

    public void setSelectedProduct(Product product) {
        if (surfaceLayer != null)
            surfaceLayer.setSelectedProduct(product);
    }

    public Product getSelectedProduct() {
        if (surfaceLayer != null)
            return surfaceLayer.getSelectedProduct();
        return null;
    }

    public void setProducts(Product[] products) {
        if (surfaceLayer != null) {
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
        surfaceLayer.removeProduct(product);
    }

    private WMSLayersPanel addTab(int position, String server)
        {
            // Add a server to the tabbed dialog.
            try
            {
                final WMSLayersPanel layersPanel = new WMSLayersPanel(wwjPanel.getWwd(), server, wmsPanelSize);
                this.tabbedPane.add(layersPanel, BorderLayout.CENTER);
                final String title = layersPanel.getServerDisplayString();
                this.tabbedPane.setTitleAt(position, title != null && title.length() > 0 ? title : server);

                // Add a listener to notice wms layer selections and tell the layer panel to reflect the new state.
                layersPanel.addPropertyChangeListener("LayersPanelUpdated", new PropertyChangeListener()
                {
                    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
                    {
                        layerPanel.update(wwjPanel.getWwd());
                    }
                });

                return layersPanel;
            }
            catch (URISyntaxException e)
            {
                JOptionPane.showMessageDialog(null, "Server URL is invalid", "Invalid Server URL",
                    JOptionPane.ERROR_MESSAGE);
                tabbedPane.setSelectedIndex(previousTabIndex);
                return null;
            }
        }

    private static ElevationModel makeElevationModel() throws URISyntaxException, ParserConfigurationException,
                                                        IOException, SAXException {
        final URI serverURI = new URI("http://www.nasa.network.com/elev");

        final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        if (Configuration.getJavaVersion() >= 1.6) {
            try {
                docBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            }
            catch (ParserConfigurationException e) {   // Note it and continue on. Some Java5 parsers don't support the feature.
                String message = Logging.getMessage("XML.NonvalidatingNotSupported");
                Logging.logger().finest(message);
            }
        }
        final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

        // Request the capabilities document from the server.
        final CapabilitiesRequest req = new CapabilitiesRequest(serverURI);
        final Document doc = docBuilder.parse(req.toString());

        // Parse the DOM as a capabilities document.
        final Capabilities caps = Capabilities.parse(doc);

        final double HEIGHT_OF_MT_EVEREST = 8850d; // meters
        final double DEPTH_OF_MARIANAS_TRENCH = -11000d; // meters

        // Set up and instantiate the elevation model
        final AVList params = new AVListImpl();
        params.setValue(AVKey.LAYER_NAMES, "|srtm3");
        params.setValue(AVKey.TILE_WIDTH, 150);
        params.setValue(AVKey.TILE_HEIGHT, 150);
        params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, LatLon.fromDegrees(20, 20));
        params.setValue(AVKey.NUM_LEVELS, 8);
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
        params.setValue(AVKey.ELEVATION_MIN, DEPTH_OF_MARIANAS_TRENCH);
        params.setValue(AVKey.ELEVATION_MAX, HEIGHT_OF_MT_EVEREST);

        final CompoundElevationModel cem = new CompoundElevationModel();
        cem.addElevationModel(new WMSBasicElevationModel(caps, params));

        return cem;
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

            // Setup a select listener for the worldmap click-and-go feature
            this.wwd.addSelectListener(new ClickAndGoSelectListener(this.getWwd(), WorldMapLayer.class));

            this.add(this.wwd, BorderLayout.CENTER);
            if (includeStatusBar) {
                this.statusBar = new StatusBar();
                this.add(statusBar, BorderLayout.PAGE_END);
                this.statusBar.setEventSource(wwd);
            }

            m.getLayers().add(new LayerPanelLayer(getWwd()));

            try {
                final ElevationModel em = makeElevationModel();
                m.getGlobe().setElevationModel(em);
            } catch(Exception e) {
                //
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
