
package org.esa.nest.dat;

import com.bc.ceres.core.Assert;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.core.runtime.Module;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.bc.layer.LayerModel;
import com.bc.swing.desktop.TabbedDesktopPane;
import com.jidesoft.action.CommandBar;
import com.jidesoft.action.CommandMenuBar;
import com.jidesoft.action.DockableBarContext;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.*;
import org.esa.beam.framework.ui.application.ApplicationPage;
import org.esa.beam.framework.ui.application.ApplicationWindow;
import org.esa.beam.framework.ui.application.ToolViewDescriptor;
import org.esa.beam.framework.ui.command.Command;
import org.esa.beam.framework.ui.product.*;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.util.*;
import org.esa.beam.visat.*;

import javax.swing.*;
import javax.swing.event.InternalFrameListener;
import java.awt.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URL;

/**
 * The <code>DatApp</code> class represents the DAT application.
 *
 * @author Norman Fomferra
 * @author Sabine Embacher
 * @version $Revision: 1.10 $ $Date: 2008-01-23 19:51:56 $
 */
public final class DatApp extends VisatApp {

    static {
        DatActivator activator = DatActivator.getInstance();
        if (activator != null) {
            Module module = activator.getModuleContext().getModule();
            APP_SYMBOLIC_NAME = module.getSymbolicName();
            APP_VERSION = module.getVersion().toString();
            APP_COPYRIGHTINFO = module.getCopyright();
            APP_LOGGER_NAME = module.getSymbolicName();
        } else {
            APP_SYMBOLIC_NAME = "nest-dat";
            APP_VERSION = "0.1";
            APP_COPYRIGHTINFO = "";
            APP_LOGGER_NAME = "nest-dat";
        }
    }

    /**
     * Application Name
     */
    public static final String APP_NAME = "NEST DAT";
    /**
     * Application symbolic name
     */
    public static final String APP_SYMBOLIC_NAME;
    /**
     * Application Version
     */
    public static final String APP_VERSION;
    /**
     * Application Copyright Information
     */
    public static final String APP_COPYRIGHTINFO;
    /**
     * The name of the system logger
     */
    public static final String APP_LOGGER_NAME;
    /**
     * VISAT's plug-in directory
     */
    public static final String APP_DEFAULT_PLUGIN_DIR = SystemUtils.EXTENSION_DIR_NAME;

    /**
     * Preferences key for automatic data unload
     */
    public static final String PROPERTY_KEY_AUTO_UNLOAD_DATA = "visat.autounload.enabled";
    /**
     * Preferences key for automatically showing new bands
     */
    public static final String PROPERTY_KEY_AUTO_SHOW_NEW_BANDS = "visat.autoshowbands.enabled";
    /**
     * Preferences key for automatically showing magnifier
     */
    public static final String PROPERTY_KEY_AUTO_SHOW_MAGNIFIER = "visat.autoshowmagnifier.enabled";
    /**
     * Preferences key for automatically showing navigation
     */
    public static final String PROPERTY_KEY_AUTO_SHOW_NAVIGATION = "visat.autoshownavigation.enabled";
    /**
     * Preferences key for on-line version check
     */
    public static final String PROPERTY_KEY_VERSION_CHECK_ENABLED = "visat.versionCheck" + SuppressibleOptionPane.KEY_PREFIX_ENABLED;
    /**
     * Preferences key for on-line version question
     */
    public static final String PROPERTY_KEY_VERSION_CHECK_DONT_ASK = "visat.versionCheck" + SuppressibleOptionPane.KEY_PREFIX_DONT_SHOW;

    /**
     * The one and only visat instance
     */
    private static DatApp instance;

    // todo use instead
    private VisatApplicationPage applicationPage;
    private VisatApplicationWindow window;

    private DatProductsToolView productsToolView;
    private final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    /**
     * Constructs the VISAT application instance. The method does not start the application nor does it perform any GUI
     * work.
     */
    private DatApp() {
        super(APP_NAME,
              APP_SYMBOLIC_NAME,
              APP_VERSION,
              APP_COPYRIGHTINFO,
              null,
              null);
    }


    /**
     * Starts this application.
     *
     * @see DatMain#run(Object, ProgressMonitor)
     */
    public static void start(ProgressMonitor pm) throws Exception {
        if (instance == null) {
            instance = new DatApp();
            instance.startUp(pm);
        }
    }

    @Override
    protected void initClient(ProgressMonitor pm) {

        try {
            pm.beginTask("Initialising DAT components", 3);

            internalFrameListeners = new ArrayList<InternalFrameListener>(10);
            propertyMapChangeListeners = new ArrayList<PropertyMapChangeListener>(4);
            productManager = new ProductManager();
            productNodeListener = createProductNodeListener();

            getMainFrame().getDockingManager().setHideFloatingFramesOnSwitchOutOfApplication(true);
            getMainFrame().getDockingManager().setHideFloatingFramesWhenDeactivate(false);

            desktopPane = new TabbedDesktopPane();

            window = new VisatApplicationWindow(this);
            applicationPage = new VisatApplicationPage(desktopPane, getMainFrame().getDockingManager());
            applicationPage.setWindow(window);
            window.setPage(applicationPage);

            pm.setTaskName("Loading commands");
            Command[] commands = DatActivator.getInstance().getCommands();
            for (Command command : commands) {
                addCommand(command, getCommandManager());
            }
            pm.worked(1);

            pm.setTaskName("Loading tool windows");
            loadToolViews();
            pm.worked(1);

            pm.setTaskName("Starting plugins");
            plugInManager = new VisatPlugInManager(DatActivator.getInstance().getPlugins());
            plugInManager.startPlugins();
            registerShowToolViewCommands();
            pm.worked(1);

        } finally {
            pm.done();
        }
    }

    @Override
    protected void initClientUI(ProgressMonitor pm) {
        try {
            pm.beginTask("Initialising DAT UI components", 4);

            CommandBar layersToolBar = createLayersToolBar();
            layersToolBar.getContext().setInitSide(DockableBarContext.DOCK_SIDE_NORTH);
            layersToolBar.getContext().setInitIndex(1);
            getMainFrame().getDockableBarManager().addDockableBar(layersToolBar);
            pm.worked(1);

            CommandBar analysisToolBar = createAnalysisToolBar();
            analysisToolBar.getContext().setInitSide(DockableBarContext.DOCK_SIDE_NORTH);
            analysisToolBar.getContext().setInitIndex(1);
            getMainFrame().getDockableBarManager().addDockableBar(analysisToolBar);
            pm.worked(1);

            CommandBar toolsToolBar = createToolsToolBar();
            toolsToolBar.getContext().setInitSide(DockableBarContext.DOCK_SIDE_EAST);
            toolsToolBar.getContext().setInitIndex(0);
            getMainFrame().getDockableBarManager().addDockableBar(toolsToolBar);
            pm.worked(1);

            CommandBar viewsToolBar = createViewsToolBar();
            viewsToolBar.getContext().setInitSide(DockableBarContext.DOCK_SIDE_NORTH);
            viewsToolBar.getContext().setInitIndex(1);
            getMainFrame().getDockableBarManager().addDockableBar(viewsToolBar);
            pm.worked(1);

        } finally {
            pm.done();
        }
    }

    public VisatActivator getActivator() {
        return DatActivator.getInstance();
    }

    public final ApplicationWindow getWindow() {
        return window;
    }

    public final ApplicationPage getPage() {
        return window.getPage();
    }

    /**
     * Creates a default frame icon for this application.
     * <p/> Override this method if you want another behaviour.
     *
     * @return the frame icon, or <code>null</code> if no icon is used
     */
    protected ImageIcon createFrameIcon() {
        URL iconURL = getClass().getResource("/icons/WorldMap24.gif");
        if (iconURL == null) {
            return null;
        }
        return new ImageIcon(iconURL);
    }

    private void loadToolViews() {
        ToolViewDescriptor[] toolViewDescriptors = DatActivator.getInstance().getToolViewDescriptors();
        for (ToolViewDescriptor toolViewDescriptor : toolViewDescriptors) {
            applicationPage.addToolView(toolViewDescriptor);
        }
        productsToolView = (DatProductsToolView) applicationPage.getToolView(DatProductsToolView.ID);
        Assert.state(productsToolView != null, "productsToolView != null");
    }

    /**
     * Resets the singleton application instance so that {@link #getApp()} will return <code>null</code> after this method has been called.
     */
    @Override
    protected void handleImminentExit() {
        if (plugInManager != null) {
            plugInManager.stopPlugins();
        }
        singleThreadExecutor.shutdown();
        super.handleImminentExit();
    }

    private void registerShowToolViewCommands() {

        ToolViewDescriptor[] toolViewDescriptors = DatActivator.getInstance().getToolViewDescriptors();
        for (ToolViewDescriptor toolViewDescriptor : toolViewDescriptors) {
            // triggers also command registration in command manager
            toolViewDescriptor.createShowViewCommand(window);
        }
    }

    /**
     * Returns the one and only VISAT application instance (singleton).
     *
     * @return the VISAT application. If it has not been started so far, <code>null</code> is returned.
     */
    public static DatApp getApp() {
        return instance;
    }

    /**
     * Adds a product tree listener to VISAT. Product tree listeners are notified each time a product node is selected
     * or double-clicked within VISAT's product tree browser. Product nodes comprise a product itself, its bands,
     * tie-point grids or metadata elements.
     *
     * @param listener the listener to be added
     */
    public void addProductTreeListener(final ProductTreeListener listener) {
        if (productsToolView == null) {
            throw new IllegalStateException("productsToolView == null");
        }
        productsToolView.getProductTree().addProductTreeListener(listener);
    }

    /**
     * Removes a product tree listener from VISAT. Product tree listeners are notified each time a product node is
     * selected or double-clicked within VISAT's product tree browser. Product nodes comprise a product itself, its
     * bands, tie-point grids or metadata elements.
     *
     * @param listener the listener to be removed
     */
    public void removeProductTreeListener(final ProductTreeListener listener) {
        if (productsToolView == null) {
            throw new IllegalStateException("_productstoolView == null");
        }
        productsToolView.getProductTree().removeProductTreeListener(listener);
    }

    /**
     * Returns VISAT's product tree browser.
     */
    public ProductTree getProductTree() {
        return productsToolView.getProductTree();
    }

    public synchronized Product newProduct() {
        return newProductImpl();
    }

    /**
     * Creates a new product scene view and opens an internal frame for it.
     */
    public void openProductSceneView(final RasterDataNode raster, final String helpId) {
        final SwingWorker worker = new ProgressMonitorSwingWorker<ProductSceneImage, Object>(getMainFrame(),
                                                                                             "Creating single band image") {

            @Override
            protected ProductSceneImage doInBackground(ProgressMonitor pm) throws Exception {
                try {
                    return createProductSceneImage(raster, helpId, pm);
                } finally {
                    if (pm.isCanceled()) {
                        raster.unloadRasterData();
                    }
                }
            }

            @Override
            public void done() {
                ProductSceneImage productSceneImage;
                try {
                    productSceneImage = get();
                } catch (Exception e) {
                    // should not happen
                    return;
                }

                if (productSceneImage == null) {
                    return;
                }

                ProductSceneView productSceneView = new ProductSceneView(productSceneImage);
                productSceneView.setCommandUIFactory(getCommandUIFactory());
                productSceneView.setROIOverlayEnabled(true);
                productSceneView.setGraticuleOverlayEnabled(false);
                productSceneView.setPinOverlayEnabled(true);
                productSceneView.setLayerProperties(getPreferences());
                productSceneView.addImageUpdateListener(new ProductSceneView.ImageUpdateListener() {
                    public void handleImageUpdated(final ProductSceneView view) {
                        updateState();
                    }
                });
                final String title = createInternalFrameTitle(raster);
                final Icon icon = UIUtils.loadImageIcon("icons/RsBandAsSwath16.gif");
                final JInternalFrame internalFrame = createInternalFrame(title, icon, productSceneView, helpId);
                final Product product = raster.getProduct();
                product.addProductNodeListener(new ProductNodeListenerAdapter() {
                    @Override
                    public void nodeChanged(final ProductNodeEvent event) {
                        if (event.getSourceNode() == raster &&
                            event.getPropertyName().equalsIgnoreCase(ProductNode.PROPERTY_NAME_NAME)) {
                            internalFrame.setTitle(createInternalFrameTitle(raster));
                        }
                    }
                });

// @todo 1 nf/nf - extract layer properties dialog from VISAT preferences
// note: the following line has been out-commented because a preferences change is reflected
// in all open product scene views. The actual solution is to extract a layer properties dialog
// from the preferences which lets a user edit the properties of the current product scene view.
//            addPropertyMapChangeListener(productSceneView);
                updateState();
            }
        };
        singleThreadExecutor.submit(worker);
    }

    /**
     * Creates a new rgb product scene view and opens an internal frame for it.
     */
    public void openProductSceneViewRGB(final Product product, final String helpId) {
        final SwingWorker worker = new ProgressMonitorSwingWorker<ProductSceneImage, Object>(getMainFrame(),
                                                                                             "Create RGB Image View") {

            @Override
            protected ProductSceneImage doInBackground(ProgressMonitor pm) throws Exception {
                return createRGBProductSceneImage(product, helpId, pm);
            }

            @Override
            protected void done() {
                ProductSceneImage productSceneImage;
                try {
                    productSceneImage = get();
                } catch (Exception e) {
                    return;
                }
                if (productSceneImage == null) {
                    return;
                }
                ProductSceneView productSceneView = new ProductSceneView(productSceneImage);
                productSceneView.setCommandUIFactory(getCommandUIFactory());
                productSceneView.setNoDataOverlayEnabled(false);
                productSceneView.setROIOverlayEnabled(false);
                productSceneView.setGraticuleOverlayEnabled(false);
                productSceneView.setPinOverlayEnabled(false);
                productSceneView.setLayerProperties(getPreferences());
                final String title = createInternalFrameTitleRGB(product, productSceneImage.getName());
                final Icon icon = UIUtils.loadImageIcon("icons/RsBandAsSwath16.gif");
                createInternalFrame(title, icon, productSceneView, helpId);
                addPropertyMapChangeListener(productSceneView);
                updateState();
            }
        };
        singleThreadExecutor.submit(worker);
    }

    /**
     * Shows VISAT's about box.
     */
    public void showAboutBox() {
        final DatAboutBox box = new DatAboutBox();
        box.show();
    }

    private Product newProductImpl() {
        if (getProductManager().getNumProducts() == 0) {
            return null;
        }
        final ProductNodeList<Product> products = new ProductNodeList<Product>();
        products.copyInto(getProductManager().getProducts());
        final Product selectedProduct = getSelectedProduct();
        if (selectedProduct == null) {
            return null;
        }
        final int selectedSourceIndex = products.indexOf(selectedProduct);
        final NewProductDialog dialog = new NewProductDialog(getMainFrame(), products, selectedSourceIndex, false);
        if (dialog.show() != NewProductDialog.ID_OK) {
            return null;
        }
        final Product product = dialog.getResultProduct();
        if (product != null) {
            addProduct(product);
            updateState();
        }
        return product;
    }

    private ProductSceneImage createProductSceneImage(final RasterDataNode raster, final String helpId,
                                                      ProgressMonitor pm) {
        Debug.assertNotNull(raster);
        final String message = "Creating image view...";
        setStatusBarMessage(message);
        UIUtils.setRootFrameWaitCursor(getMainFrame());

        final boolean mustLoadData;
        // JAIJAIJAI
        if (Boolean.getBoolean("beam.imageTiling.enabled")) {
            mustLoadData = false;
        } else {
            final long dataAutoLoadMemLimit = getDataAutoLoadLimit();
            mustLoadData = raster.getRasterDataSizeInBytes() < dataAutoLoadMemLimit;
        }

        ProductSceneImage productSceneImage = null;
        pm.beginTask(message, mustLoadData ? 2 : 1);
        try {
            if (mustLoadData) {
                loadProductRasterDataImpl(raster, SubProgressMonitor.create(pm, 1));
                if (!raster.hasRasterData()) {
                    return null;
                }
            }
            final JInternalFrame[] frames = findInternalFrames(raster, 1);
            final LayerModel layerModel;
            if (frames.length > 0) {
                final ProductSceneView view = (ProductSceneView) frames[0].getContentPane();
                layerModel = view.getImageDisplay().getLayerModel();
            } else {
                layerModel = null;
            }
            if (layerModel != null) {
                productSceneImage = ProductSceneImage.create(raster, layerModel, SubProgressMonitor.create(pm, 1));
            } else {
                productSceneImage = ProductSceneImage.create(raster, SubProgressMonitor.create(pm, 1));
            }
        } catch (OutOfMemoryError e) {
            showOutOfMemoryErrorDialog("The image view could not be created.");
        } catch (Exception e) {
            handleUnknownException(e);
        } finally {
            pm.done();
        }
        UIUtils.setRootFrameDefaultCursor(getMainFrame());
        clearStatusBarMessage();

        return productSceneImage;
    }

    private String createInternalFrameTitle(final RasterDataNode raster) {
        return UIUtils.getUniqueFrameTitle(getAllInternalFrames(), raster.getDisplayName());
    }

    private long getDataAutoLoadLimit() {
        final long megabyte = 1024 * 1024;
        return megabyte * getPreferences().getPropertyInt(PROPERTY_KEY_AUTO_LOAD_DATA_LIMIT,
                                                          PROPERTY_DEFAULT_AUTO_LOAD_DATA_LIMIT);
    }

    private static class RGBBand {

        private Band band;
        private boolean dataLoaded;
    }

    private ProductSceneImage createRGBProductSceneImage(final Product product, final String helpId,
                                                         ProgressMonitor pm) {
        final RGBImageProfilePane profilePane = new RGBImageProfilePane(getPreferences(), product);
        final boolean ok = profilePane.showDialog(getMainFrame(), "Select RGB-Image Channels", helpId);
        if (!ok) {
            return null;
        }
        final String[] rgbaExpressions = profilePane.getRgbaExpressions();
        if (profilePane.getStoreProfileInProduct()) {
            RGBImageProfile.storeRgbaExpressions(product, rgbaExpressions);
        }

        setStatusBarMessage("Creating RGB image view...");  /*I18N*/
        getMainFrame().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        pm.beginTask("Creating RGB image view...", 2);
        RGBBand[] rgbBands = null;
        boolean errorOccured = false;
        ProductSceneImage productSceneImage = null;
        try {
            rgbBands = allocateRgbBands(product, rgbaExpressions, getDataAutoLoadLimit(),
                                        SubProgressMonitor.create(pm, 1));
            productSceneImage = ProductSceneImage.create(rgbBands[0].band, rgbBands[1].band, rgbBands[2].band,
                                                         SubProgressMonitor.create(pm, 1));
            final RGBImageProfile selectedProfile = profilePane.getSelectedProfile();
            final String name = selectedProfile != null ? selectedProfile.getName().replace("_", " ") : "";
            productSceneImage.setName(name);
        } catch (OutOfMemoryError e) {
            errorOccured = true;
            showOutOfMemoryErrorDialog("The RGB image view could not be created."); /*I18N*/
        } catch (Exception e) {
            errorOccured = true;
            handleUnknownException(e);
        } finally {
            pm.done();
            getMainFrame().setCursor(Cursor.getDefaultCursor());
            clearStatusBarMessage();
            if (rgbBands != null) {
                releaseRgbBands(rgbBands, errorOccured);
            }
        }
        return productSceneImage;
    }

    private static RGBBand[] allocateRgbBands(final Product product,
                                              final String[] rgbaExpressions,
                                              final long dataAutoLoadMemLimit,
                                              final ProgressMonitor pm) throws IOException {
        final RGBBand[] rgbBands = new RGBBand[3]; // todo - set to [4] as soon as we support alpha
        long storageMem = 0;
        for (int i = 0; i < rgbBands.length; i++) {
            final RGBBand rgbBand = new RGBBand();
            String expression = rgbaExpressions[i].isEmpty() ? "0" : rgbaExpressions[i];
            rgbBand.band = product.getBand(expression);
            if (rgbBand.band == null) {
                rgbBand.band = new ProductSceneView.RGBChannel(product,
                                                               RGBImageProfile.RGB_BAND_NAMES[i],
                                                               expression);
            }
            rgbBands[i] = rgbBand;
            storageMem += rgbBand.band.getRawStorageSize();
        }
        // JAIJAIJAI
        if (Boolean.getBoolean("beam.imageTiling.enabled")) {
            // don't need to load any data!
        } else {
            if (storageMem < dataAutoLoadMemLimit) {
                pm.beginTask("Loading RGB channels...", rgbBands.length);
                String msgPattern = "Loading RGB channel ''{0}''...";
                for (final RGBBand rgbBand : rgbBands) {
                    if (!rgbBand.band.hasRasterData()) {
                        pm.setSubTaskName(MessageFormat.format(msgPattern, rgbBand.band.getName()));
                        rgbBand.band.loadRasterData(SubProgressMonitor.create(pm, 1));
                        rgbBand.dataLoaded = true;
                    } else {
                        pm.worked(1);
                    }
                    if (pm.isCanceled()) {
                        throw new IOException("Image creation canceled by user.");
                    }
                }
            }
        }
        return rgbBands;
    }

    private static void releaseRgbBands(RGBBand[] rgbBands, boolean errorOccured) {
        for (int i = 0; i < rgbBands.length; i++) {
            final RGBBand rgbBand = rgbBands[i];
            if (rgbBand != null && rgbBand.band != null) {
                if (rgbBand.band instanceof ProductSceneView.RGBChannel) {
                    if (rgbBand.dataLoaded) {
                        rgbBand.band.unloadRasterData();
                    }
                    if (errorOccured) {
                        rgbBand.band.dispose();
                    }
                }
                rgbBand.band = null;
            }
            rgbBands[i] = null;
        }
    }

    private String createInternalFrameTitleRGB(final Product product, String name) {
        return UIUtils.getUniqueFrameTitle(getAllInternalFrames(),
                                           product.getProductRefString() + " " + name + " RGB");
    }

    private ProductMetadataView createProductMetadataViewImpl(final MetadataElement element) {
        ProductMetadataView metadataView = null;

        setStatusBarMessage("Creating metadata view...");
        getMainFrame().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            metadataView = new ProductMetadataView(element);
            metadataView.setCommandUIFactory(getCommandUIFactory());
            final Icon icon = UIUtils.loadImageIcon("icons/RsMetaData16.gif");
            final JInternalFrame internalFrame = createInternalFrame(element.getDisplayName(),
                                                                     icon,
                                                                     metadataView, null);
            final Product product = metadataView.getProduct();
            product.addProductNodeListener(new ProductNodeListenerAdapter() {
                @Override
                public void nodeChanged(final ProductNodeEvent event) {
                    if (event.getSourceNode() == element &&
                        event.getPropertyName().equalsIgnoreCase(ProductNode.PROPERTY_NAME_NAME)) {
                        internalFrame.setTitle(element.getDisplayName());
                    }
                }
            });
            updateState();
        } catch (Exception e) {
            handleUnknownException(e);
        }

        getMainFrame().setCursor(Cursor.getDefaultCursor());
        clearStatusBarMessage();

        return metadataView;
    }

    private boolean loadProductRasterDataImpl(final RasterDataNode raster, ProgressMonitor pm) {
        if (raster.hasRasterData()) {
            return true;
        }

        setStatusBarMessage("Loading raster data...");
        // Don't show wait cursor here - progress bar should pop-up soon...

        boolean state = false;
        try {
            raster.loadRasterData(pm);
            updateState();
            state = true;
        } catch (Exception e) {
            handleUnknownException(e);
        }

        clearStatusBarMessage();
        return state;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // UI Creation

    /**
     * Overrides the base class version in order to create a tool bar for VISAT.
     */
    @Override
    protected CommandBar createMainToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("mainToolBar");
        toolBar.setTitle("Standard");
        toolBar.addDockableBarListener(new ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                "new",
                "open",
                "save",
                null,
                "preferences",
                "properties",
                null,
                "showUpdateDialog",
                "helpTopics",
        });

        return toolBar;
    }

    private CommandBar createLayersToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("layersToolBar");
        toolBar.setTitle("Layers");
        toolBar.addDockableBarListener(new ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                "showNoDataOverlay",
                "showROIOverlay",
                "showShapeOverlay",
                "showGraticuleOverlay",
                PinDescriptor.INSTANCE.getShowLayerCommandId(),
                GcpDescriptor.INSTANCE.getShowLayerCommandId(),
        });

        return toolBar;
    }

    private CommandBar createAnalysisToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("analysisToolBar");
        toolBar.setTitle("Analysis");
        toolBar.addDockableBarListener(new ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                "openInformationDialog",
                "openGeoCodingInfoDialog",
                "openStatisticsDialog",
                "openHistogramDialog",
                "openScatterPlotDialog",
        });

        return toolBar;
    }

    private CommandBar createToolsToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("toolsToolBar");
        toolBar.setTitle("Tools");
        toolBar.addDockableBarListener(new VisatApp.ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                // These IDs are defined in the module.xml
                "selectTool",
                "rangeFinder",
                "zoomTool",
                "pannerTool",
                "pinTool",
                "gcpTool",
                "drawLineTool",
                "drawRectangleTool",
                "drawEllipseTool",
                "drawPolylineTool",
                "drawPolygonTool",
                "deleteShape",
                "magicStickTool",
                null,
                "convertShapeToROI",
                "convertROIToShape",
        });

        return toolBar;
    }

    private CommandBar createViewsToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("viewsToolBar");
        toolBar.setTitle("Views");
        toolBar.addDockableBarListener(new VisatApp.ToolBarListener());

        ToolViewDescriptor[] toolViewDescriptors = DatActivator.getInstance().getToolViewDescriptors();
        List<String> viewCommandIdList = new ArrayList<String>(5);
        for (ToolViewDescriptor toolViewDescriptor : toolViewDescriptors) {
            if (!"org.esa.beam.visat.toolviews.stat.StatisticsToolView".equals(toolViewDescriptor.getId())) {
                viewCommandIdList.add(toolViewDescriptor.getId() + ".showCmd");
            }
        }

        addCommandsToToolBar(toolBar, viewCommandIdList.toArray(new String[viewCommandIdList.size()]));

        return toolBar;

    }

    private void addCommandsToToolBar(CommandBar toolBar, String[] commandIDs) {
        for (final String commandID : commandIDs) {
            if (commandID == null) {
                toolBar.add(ToolButtonFactory.createToolBarSeparator());
            } else {
                final Command command = getCommandManager().getCommand(commandID);
                if(command != null) {
                    final AbstractButton toolBarButton = command.createToolBarButton();
                    toolBarButton.addMouseListener(getMouseOverActionHandler());
                    toolBar.add(toolBarButton);
                }
            }
            toolBar.add(Box.createHorizontalStrut(1));
        }
    }

    /**
     * Overrides the base class version in order to creates the menu bar for VISAT.
     */
    @Override
    protected CommandBar createMainMenuBar() {
        final CommandMenuBar menuBar = new CommandMenuBar("Main Menu");
        menuBar.setHidable(false);
        menuBar.setStretch(true);

        menuBar.add(createJMenu("file", "File", 'F')); /*I18N*/
        menuBar.add(createJMenu("edit", "Edit", 'E')); /*I18N*/
        menuBar.add(createJMenu("view", "View", 'V'));  /*I18N*/
        menuBar.add(createJMenu("data", "Analysis", 'A')); /*I18N*/
        menuBar.add(createJMenu("tools", "Tools", 'T')); /*I18N*/
        menuBar.add(createJMenu("window", "Window", 'W')); /*I18N*/
        menuBar.add(createJMenu("help", "Help", 'H')); /*I18N*/

        return menuBar;
    }

    /**
     * Creates a new product metadata view and opens an internal frame for it.
     */
    public synchronized ProductMetadataView createProductMetadataView(final MetadataElement element) {
        return createProductMetadataViewImpl(element);
    }

}