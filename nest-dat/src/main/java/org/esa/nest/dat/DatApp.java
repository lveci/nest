
package org.esa.nest.dat;

import com.jidesoft.action.CommandBar;
import com.jidesoft.action.CommandMenuBar;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.ui.*;
import org.esa.beam.framework.ui.application.ApplicationDescriptor;
import org.esa.beam.framework.ui.application.ToolViewDescriptor;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.visat.*;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;

public final class DatApp extends VisatApp {
    public DatApp(ApplicationDescriptor applicationDescriptor) {
        super(applicationDescriptor);

        DEFAULT_VALUE_SAVE_PRODUCT_ANNOTATIONS = true;
    }

    public static DatApp getApp() {
        return (DatApp) VisatApp.getApp();
    }

    // You can now override numerous createXXX() methods
    // to customize the application GUI

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
        String getStarted = VisatApp.getApp().getPreferences().getPropertyString("visat.showGettingStarted", "true");
        if(getStarted == null || getStarted.equals("true")) {
            HelpSys.showTheme("using_dat");
            VisatApp.getApp().getPreferences().setPropertyString("visat.showGettingStarted", "false");
            
        }
    }

    protected CommandBar createViewsToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("viewsToolBar");
        toolBar.setTitle("Views");
        toolBar.addDockableBarListener(new ToolBarListener());

        ToolViewDescriptor[] toolViewDescriptors = VisatActivator.getInstance().getToolViewDescriptors();
        List<String> viewCommandIdList = new ArrayList<String>(10);

        // add default views grouped
        viewCommandIdList.add("org.esa.nest.dat.toolviews.Projects.ProjectsToolView.showCmd");
        viewCommandIdList.add("org.esa.beam.visat.ProductsToolView.showCmd");
        viewCommandIdList.add("org.esa.beam.visat.toolviews.pixelinfo.PixelInfoToolView.showCmd");
        viewCommandIdList.add(null);
        viewCommandIdList.add("org.esa.beam.visat.toolviews.nav.NavigationToolView.showCmd");
        viewCommandIdList.add("org.esa.beam.visat.toolviews.imageinfo.ColorManipulationToolView.showCmd");
        viewCommandIdList.add(null);

        for (ToolViewDescriptor toolViewDescriptor : toolViewDescriptors) {
            String id = toolViewDescriptor.getId();
            if (id.equals("org.esa.beam.visat.toolviews.stat.StatisticsToolView") ||
                id.equals("org.esa.beam.visat.toolviews.pin.PinManagerToolView") ||
                id.equals("org.esa.beam.visat.toolviews.pin.GcpManagerToolView") ||
                id.equals("org.esa.beam.visat.toolviews.roi.RoiManagerToolView") ||
                id.equals("org.esa.beam.visat.toolviews.bitmask.BitmaskOverlayToolView") ) {
                    continue;
            }
            if(!viewCommandIdList.contains(id+".showCmd")) {
                viewCommandIdList.add(toolViewDescriptor.getId() + ".showCmd");
            }
        }

        addCommandsToToolBar(toolBar, viewCommandIdList.toArray(new String[viewCommandIdList.size()]));

        return toolBar;
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
                "openProductGrabber",
                null,
                "openRaster",
                "openVector",
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
        //JMenu insar = createJMenu("insar", "InSAR", 'I');
        //insar.setEnabled(false);
        //menuBar.add(insar); /*I18N*/
        //JMenu exploit = createJMenu("exploitation", "Exploitation", 'E');
        //exploit.setEnabled(false);
        //menuBar.add(exploit); /*I18N*/
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
        File graphPath = new File(homeUrl, File.separator + "graphs");
        if(!graphPath.exists()) return;

        createGraphMenu(menu, graphPath);
    }

    private static void createGraphMenu(JMenu menu, File path) {
        File[] filesList = path.listFiles();
        if(filesList == null || filesList.length == 0) return;

        for (final File file : filesList) {
            if(file.isDirectory() && !file.isHidden()) {
                JMenu subMenu = new JMenu(file.getName());
                menu.add(subMenu);
                createGraphMenu(subMenu, file);
            } else if(file.getName().toLowerCase().endsWith(".xml")) {
                final JMenuItem item = new JMenuItem(file.getName());
                item.addActionListener(new ActionListener() {

                    public void actionPerformed(final ActionEvent e) {
                        GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""), "Graph Builder", "graph_builder");
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
    protected CommandBar createToolsToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("toolsToolBar");
        toolBar.setTitle("Tools");
        toolBar.addDockableBarListener(new ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                // These IDs are defined in the module.xml
                "selectTool",
                "crossHairTool",
                "rangeFinder",
                "zoomTool",
                "pannerTool",
                null,
                "pinTool",
                //"org.esa.beam.visat.toolviews.pin.PinManagerToolView.showCmd",
                "gcpTool",
                //"org.esa.beam.visat.toolviews.pin.GcpManagerToolView.showCmd",
                null,
                "drawLineTool",
                "drawRectangleTool",
                "drawEllipseTool",
                //"drawPolylineTool",
                "drawPolygonTool",
                //"deleteShape",
                //"magicStickTool",
                null,
                "convertShapeToROI",
                //"org.esa.beam.visat.toolviews.roi.RoiManagerToolView.showCmd",
                //"org.esa.beam.visat.toolviews.bitmask.BitmaskOverlayToolView.showCmd",
                //"convertROIToShape",
        });

        return toolBar;
    }
}