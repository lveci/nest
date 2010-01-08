
package org.esa.nest.dat;

import com.bc.ceres.core.ProgressMonitor;
import com.jidesoft.action.CommandBar;
import com.jidesoft.action.CommandMenuBar;
import com.jidesoft.status.LabelStatusBarItem;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.application.ApplicationDescriptor;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.toolviews.diag.TileCacheDiagnosisToolView;
import org.esa.beam.visat.toolviews.stat.StatisticsToolView;
import org.esa.nest.dat.actions.LoadTabbedLayoutAction;
import org.esa.nest.dat.actions.importbrowser.ImportBrowserAction;
import org.esa.nest.dat.actions.importbrowser.model.Repository;
import org.esa.nest.dat.actions.importbrowser.ui.ImportBrowser;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;
import org.esa.nest.dat.views.polarview.PolarView;
import org.esa.nest.util.ResourceUtils;
import org.esa.nest.util.Settings;

import javax.media.jai.JAI;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public final class DatApp extends VisatApp {
    public DatApp(ApplicationDescriptor applicationDescriptor) {
        super(applicationDescriptor);

        DEFAULT_VALUE_SAVE_PRODUCT_ANNOTATIONS = true;
    }

    public static DatApp getApp() {
        return (DatApp) VisatApp.getApp();
    }

    @Override
    protected String getMainFrameTitle() {
        //return getAppName() + " " + getAppVersion();
        return getAppName() + " 3C-0.6 Beta";
    }

    // You can now override numerous createXXX() methods
    // to customize the application GUI

    @Override
    protected ModalDialog createAboutBox() {
        return new DatAboutBox();
    }

    @Override
    protected void initClientUI(ProgressMonitor pm) {
        super.initClientUI(pm);

        updateGraphMenu();
    }

    @Override
    protected void postInit() {
        final String getStarted = VisatApp.getApp().getPreferences().getPropertyString("visat.showGettingStarted", "true");
        if(getStarted == null || getStarted.equals("true")) {
            LoadTabbedLayoutAction.loadTabbedLayout();

            HelpSys.showTheme("top");
            VisatApp.getApp().getPreferences().setPropertyString("visat.showGettingStarted", "false");       
        }

        final int tileSize = Integer.parseInt(Settings.instance().get("defaultJAITileSize"));
        JAI.setDefaultTileSize(new Dimension(tileSize, tileSize));


        prefetchClasses();
    }

    private static void prefetchClasses() {
        final SwingWorker worker = new SwingWorker() {

            @Override
            protected Object doInBackground() throws Exception {                
                try {
                    final ImportBrowser ib = ImportBrowserAction.getInstance().getImportBrowser();
                    ib.getFrame().setVisible(false);
                    final Repository rep = ib.getRepositoryManager().getRepository(0);
                    if(rep != null) {
                        //ib.ShowRepository(rep);
                    }

                } catch(Exception e) {
                    VisatApp.getApp().showErrorDialog(e.getMessage());
                }
                return null;
            }
        };
        worker.execute();
    }

    @Override
    public synchronized void shutDown() {
        cleanTempFolder();

        super.shutDown();
    }

    private static void cleanTempFolder() {
        final File tempFolder = ResourceUtils.getApplicationUserTempDataDir();
        File[] fileList = tempFolder.listFiles();

        long freeSpace = tempFolder.getFreeSpace() / 1024 / 1024 / 1024;
        int cutoff = 20;
        if(freeSpace > 30)
            cutoff = 60;

        if(fileList.length > cutoff) {
            final long[] dates = new long[fileList.length];
            int i = 0;
            for(File file : fileList) {
                dates[i++] = file.lastModified();
            }
            Arrays.sort(dates);
            final long cutoffDate = dates[dates.length - cutoff];

            for(File file : fileList) {
                if(file.lastModified() < cutoffDate) {
                    file.delete();
                }
            }
        }

        fileList = tempFolder.listFiles();
        for(File file : fileList) {
            if(file.getName().startsWith("tmp_")) {
                if(file.isDirectory())
                    ResourceUtils.deleteDir(file);
                else
                    file.delete();
            }
        }
    }

    /**
     * Creates a standard status bar for this application.
     */
    @Override
    protected com.jidesoft.status.StatusBar createStatusBar() {
        final com.jidesoft.status.StatusBar statusBar = super.createStatusBar();

        final LabelStatusBarItem valueItem = new LabelStatusBarItem("STATUS_BAR_VALUE_ITEM");
        valueItem.setText("");
        valueItem.setPreferredWidth(50);
        valueItem.setAlignment(JLabel.CENTER);
        valueItem.setToolTipText("Displays pixel value");
        statusBar.add(valueItem, 3);

        final LabelStatusBarItem dimensions = new LabelStatusBarItem("STATUS_BAR_DIMENSIONS_ITEM");
        dimensions.setText("");
        dimensions.setPreferredWidth(70);
        dimensions.setAlignment(JLabel.CENTER);
        dimensions.setToolTipText("Displays image dimensions");
        statusBar.add(dimensions, 4);

        return statusBar;
    }

    @Override
    protected HashSet<String> getExcludedToolbars() {
        final HashSet<String> excludedIds = new HashSet<String>(8);
        // todo - remove bad forward dependencies to tool views (nf - 30.10.2008)
        excludedIds.add(TileCacheDiagnosisToolView.ID);
        excludedIds.add(StatisticsToolView.ID);
        excludedIds.add("org.esa.beam.scripting.visat.ScriptConsoleToolView");
        excludedIds.add("org.esa.beam.visat.toolviews.placemark.pin.PinManagerToolView");
        excludedIds.add("org.esa.beam.visat.toolviews.placemark.gcp.GcpManagerToolView");
        excludedIds.add("org.esa.nest.dat.toolviews.worldmap.NestWorldMapToolView");

        return excludedIds;
    }

    @Override
    protected void addDefaultToolViewCommands(final List<String> commandIds) {
        // add default views grouped
        commandIds.add("org.esa.nest.dat.toolviews.Projects.ProjectsToolView.showCmd");
        commandIds.add("org.esa.beam.visat.ProductsToolView.showCmd");
        commandIds.add("org.esa.beam.visat.toolviews.pixelinfo.PixelInfoToolView.showCmd");
        commandIds.add(null);
        commandIds.add("org.esa.beam.visat.toolviews.nav.NavigationToolView.showCmd");
        commandIds.add("org.esa.beam.visat.toolviews.imageinfo.ColorManipulationToolView.showCmd");
        commandIds.add("org.esa.beam.visat.toolviews.layermanager.LayerManagerToolView.showCmd");
        commandIds.add(null);
    }

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
                "newProject",
                "loadProject",
                "importBrowser",
                null,
                "openRaster",
                //"openVector",
                "save",
        });

        return toolBar;
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
        menuBar.add(createJMenu("tools", "Utilities", 'T')); /*I18N*/
        menuBar.add(createJMenu("sartools", "SAR Tools", 'S')); /*I18N*/
        menuBar.add(createJMenu("graphs", "Graphs", 'R')); /*I18N*/
        menuBar.add(createJMenu("geometry", "Geometry", 'G')); /*I18N*/
        menuBar.add(createJMenu("insar", "InSAR", 'I')); /*I18N*/
        menuBar.add(createJMenu("oceanTools", "Ocean Tools", 'O')); /*I18N*/
        menuBar.add(createJMenu("window", "Window", 'W')); /*I18N*/
        menuBar.add(createJMenu("help", "Help", 'H')); /*I18N*/

        return menuBar;
    }

    private void updateGraphMenu() {
        final JMenu menu = findMenu("graphs");
        if (menu == null) {
            return;
        }

        final String homeUrl = System.getProperty("nest.home", ".");
        final File graphPath = new File(homeUrl, File.separator + "graphs");
        if(!graphPath.exists()) return;

        createGraphMenu(menu, graphPath);
    }

    private static void createGraphMenu(final JMenu menu, final File path) {
        final File[] filesList = path.listFiles();
        if(filesList == null || filesList.length == 0) return;

        for (final File file : filesList) {
            final String name = file.getName();
            if(file.isDirectory() && !file.isHidden() && !name.equalsIgnoreCase("internal")) {
                final JMenu subMenu = new JMenu(name);
                menu.add(subMenu);
                createGraphMenu(subMenu, file);
            } else if(name.toLowerCase().endsWith(".xml")) {
                final JMenuItem item = new JMenuItem(name.substring(0, name.indexOf(".xml")));
                item.addActionListener(new ActionListener() {

                    public void actionPerformed(final ActionEvent e) {
                        final GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""),
                            "Graph Builder", "graph_builder");
                        dialog.show();
                        dialog.LoadGraph(file);
                    }
                });
                menu.add(item);
            }
        }
    }

    @Override
    protected CommandBar createAnalysisToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("analysisToolBar");
        toolBar.setTitle("Processors");
        toolBar.addDockableBarListener(new ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                "openGraphBuilderDialog",
                "editMetadata",
                "openInformationDialog",
        });

        return toolBar;
    }

    @Override
    protected CommandBar createInteractionsToolBar() {
        final CommandBar toolBar = createToolBar(INTERACTIONS_TOOL_BAR_ID, "Interactions");
        addCommandsToToolBar(toolBar, new String[]{
                // These IDs are defined in the module.xml
                "selectTool",
                //"crossHairTool",
                //"rangeFinder",
                "zoomTool",
                "pannerTool",
                null,
                "pinTool",
                "gcpTool",
                null,
                "drawLineTool",
                "drawRectangleTool",
                "drawEllipseTool",
                //"drawPolylineTool",
                "drawPolygonTool",
                //"magicStickTool",
                "createVectorDataNode",
                null,
        });

        return toolBar;
    }

        /**
     * Closes all (internal) frames associated with the given product.
     *
     * @param product The product to close the internal frames for.
     */
    @Override
    public synchronized void closeAllAssociatedFrames(final Product product) {
        super.closeAllAssociatedFrames(product);

        boolean frameFound;
        do {
            frameFound = false;
            final JInternalFrame[] frames = getDesktopPane().getAllFrames();
            if (frames == null) {
                break;
            }
            for (final JInternalFrame frame : frames) {
                final Container cont = frame.getContentPane();
                Product frameProduct = null;
                if (cont instanceof PolarView) {
                    final PolarView view = (PolarView) cont;
                    frameProduct = view.getProduct();
                }
                if (frameProduct != null && frameProduct == product) {
                    getDesktopPane().closeFrame(frame);
                    frameFound = true;
                    break;
                }
            }
        } while (frameFound);
    }
}
