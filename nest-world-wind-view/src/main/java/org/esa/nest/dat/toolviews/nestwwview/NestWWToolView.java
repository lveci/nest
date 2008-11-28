package org.esa.nest.dat.toolviews.nestwwview;

import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.File;

import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.RenderingListener;
import gov.nasa.worldwind.event.RenderingEvent;
import gov.nasa.worldwind.examples.ClickAndGoSelectListener;
import gov.nasa.worldwind.examples.LayerPanel;
import gov.nasa.worldwind.examples.StatisticsPanel;
import gov.nasa.worldwind.util.StatusBar;
import gov.nasa.worldwind.layers.WorldMapLayer;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.CompassLayer;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;

/**
 * The window displaying the world map.
 *
 * @version $Revision: 1.1 $ $Date: 2008-11-28 20:16:50 $
 */
public class NestWWToolView extends AbstractToolView {


    //private static final String loadDEMCommand = "loadDEM";
    //private static final ImageIcon loadDEMIcon = DatUtils.LoadIcon("org/esa/nest/icons/dem24.gif");

    private Dimension canvasSize = new Dimension(800, 600);

    private JPanel mainPane;
    private AppPanel wwjPanel;
    private LayerPanel layerPanel;
    private StatisticsPanel statsPanel;

    private JFileChooser fileChooser = new JFileChooser();
    private JSlider opacitySlider;
    private SurfaceImageLayer surfaceLayer;

    public NestWWToolView() {
    }

    public JComponent createControl() {

        mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.setSize(new Dimension(300,300));

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

        VisatApp.getApp().addProductTreeListener(new NestWWToolView.WWPTL());

        // Add an internal frame listener to VISAT so that we can update our
        // world map window with the information of the currently activated  product scene view.
        VisatApp.getApp().addInternalFrameListener(new NestWWToolView.WWIFL());
        setProducts(VisatApp.getApp());
        setSelectedProduct(VisatApp.getApp().getSelectedProduct());
        
        // world wind canvas
        initialize(mainPane, true, true, true);

        surfaceLayer = new SurfaceImageLayer();
        surfaceLayer.setOpacity(0.7);
        surfaceLayer.setPickEnabled(false);
        surfaceLayer.setName("Surface Images");

        insertBeforeCompass(getWwd(), surfaceLayer);

        getLayerPanel().add(makeControlPanel(), BorderLayout.SOUTH);
        getLayerPanel().update(getWwd());

        return mainPane;
    }

    public WorldWindowGLCanvas getWwd() {
        return wwjPanel.getWwd();
    }

    public StatusBar getStatusBar() {
        return wwjPanel.getStatusBar();
    }

    public LayerPanel getLayerPanel() {
        return layerPanel;
    }

    public StatisticsPanel getStatsPanel() {
        return statsPanel;
    }

    public static void insertBeforeCompass(WorldWindow wwd, Layer layer)
    {
        // Insert the surfaceLayer into the surfaceLayer list just before the compass.
        int compassPosition = 0;
        LayerList layers = wwd.getModel().getLayers();
        for (Layer l : layers)
        {
            if (l instanceof CompassLayer)
                compassPosition = layers.indexOf(l);
        }
        layers.add(compassPosition, layer);
    }

    public static class AppPanel extends JPanel
    {
        private WorldWindowGLCanvas wwd;
        private StatusBar statusBar;

        public AppPanel(Dimension canvasSize, boolean includeStatusBar)
        {
            super(new BorderLayout());

            this.wwd = new WorldWindowGLCanvas();
            this.wwd.setPreferredSize(canvasSize);

            // Create the default model as described in the current worldwind properties.
            Model m = (Model) WorldWind.createConfigurationComponent(AVKey.MODEL_CLASS_NAME);
            this.wwd.setModel(m);

            // Setup a select listener for the worldmap click-and-go feature
            this.wwd.addSelectListener(new ClickAndGoSelectListener(this.getWwd(), WorldMapLayer.class));

            this.add(this.wwd, BorderLayout.CENTER);
            if (includeStatusBar)
            {
                this.statusBar = new StatusBar();
                this.add(statusBar, BorderLayout.PAGE_END);
                this.statusBar.setEventSource(wwd);
            }
        }

        public WorldWindowGLCanvas getWwd() {
            return wwd;
        }

        public StatusBar getStatusBar() {
            return statusBar;
        }
    }

    private void initialize(JPanel mainPane, boolean includeStatusBar, boolean includeLayerPanel, boolean includeStatsPanel) {
        // Create the WorldWindow.
        wwjPanel = new AppPanel(canvasSize, includeStatusBar);
        wwjPanel.setPreferredSize(canvasSize);

        // Put the pieces together.
        mainPane.add(wwjPanel, BorderLayout.CENTER);
        if (includeLayerPanel)
        {
            layerPanel = new LayerPanel(wwjPanel.getWwd(), null);
            mainPane.add(layerPanel, BorderLayout.WEST);
        }
        if (includeStatsPanel)
        {
            statsPanel = new StatisticsPanel(wwjPanel.getWwd(), new Dimension(250, canvasSize.height));
            mainPane.add(statsPanel, BorderLayout.EAST);
            wwjPanel.getWwd().addRenderingListener(new RenderingListener()
            {
                public void stageChanged(RenderingEvent event)
                {
                    if (event.getSource() instanceof WorldWindow)
                    {
                        EventQueue.invokeLater(new Runnable()
                        {
                            public void run()
                            {
                                statsPanel.update(wwjPanel.getWwd());
                            }
                        });
                    }
                }
            });
        }
    }

    private JPanel makeControlPanel()
        {
            JPanel controlPanel = new JPanel(new GridLayout(0, 1, 5, 5));
            JButton openButton = new JButton("Open Image File...");
            controlPanel.add(openButton);
            openButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent actionEvent)
                {
                    int status = fileChooser.showOpenDialog(mainPane);
                    if (status != JFileChooser.APPROVE_OPTION)
                        return;

                    File imageFile = fileChooser.getSelectedFile();
                    if (imageFile == null)
                        return;

                    try
                    {
                        surfaceLayer.addImage(imageFile.getAbsolutePath());
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });

            opacitySlider = new JSlider();
            opacitySlider.setMaximum(100);
            opacitySlider.setValue((int) (surfaceLayer.getOpacity() * 100));
            opacitySlider.setEnabled(true);
            opacitySlider.addChangeListener(new ChangeListener()
            {
                public void stateChanged(ChangeEvent e)
                {
                    int value = opacitySlider.getValue();
                    surfaceLayer.setOpacity(value / 100d);
                    getWwd().repaint();
                }
            });
            JPanel opacityPanel = new JPanel(new BorderLayout(5, 5));
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
        if(surfaceLayer != null)
            surfaceLayer.setSelectedProduct(product);
    }

    public Product getSelectedProduct() {
        if(surfaceLayer != null)
            return surfaceLayer.getSelectedProduct();
        return null;
    }

    private void setProducts(final VisatApp visatApp) {
        setProducts(visatApp.getProductManager().getProducts());
    }

    public void setProducts(Product[] products) {
        if(surfaceLayer != null)
            surfaceLayer.setProducts(products);
    }

    public void setPathesToDisplay(GeoPos[][] geoBoundaries) {
        //_painter.setPathesToDisplay(geoBoundaries);
    }

    private class WWPTL implements ProductTreeListener {

        public WWPTL() {
        }

        public void productAdded(final Product product) {
            setSelectedProduct(product);
            setProducts(VisatApp.getApp());
        }

        public void productRemoved(final Product product) {
            if (getSelectedProduct() == product) {
                setSelectedProduct(product);
            }
            setProducts(VisatApp.getApp());
        }

        public void productSelected(final Product product, final int clickCount) {
            setSelectedProduct(product);
        }

        public void metadataElementSelected(final MetadataElement group, final int clickCount) {
            final Product product = group.getProduct();
            setSelectedProduct(product);
        }

        public void tiePointGridSelected(final TiePointGrid tiePointGrid, final int clickCount) {
            final Product product = tiePointGrid.getProduct();
            setSelectedProduct(product);
        }

        public void bandSelected(final Band band, final int clickCount) {
            final Product product = band.getProduct();
            setSelectedProduct(product);
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
