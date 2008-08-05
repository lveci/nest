
package org.esa.nest.dat;

import com.jidesoft.action.CommandBar;
import com.jidesoft.action.CommandMenuBar;
import com.jidesoft.action.DockableBarContext;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.ui.*;
import org.esa.beam.framework.ui.application.ApplicationDescriptor;
import org.esa.beam.framework.ui.application.ToolViewDescriptor;
import org.esa.beam.visat.*;

import java.beans.PropertyVetoException;
import java.util.List;
import java.util.ArrayList;

public final class DatApp extends VisatApp {
    public DatApp(ApplicationDescriptor applicationDescriptor) {
        super(applicationDescriptor);
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
        try {
            pm.beginTask(String.format("Initialising %s UI components", getAppName()), 4);

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

           /* if (ProductSceneImage.isInTiledImagingMode()) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        instance.showInfoDialog("You are using " + getAppName() + " in a new imaging mode.\n" +
                                "THIS MODE IS STILL UNDER DEVELOPMENT!",
                                                "beam.imageTiling.warn");
                    }
                });
            }     */

        } finally {
            pm.done();
        }
    }

    protected CommandBar createViewsToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("viewsToolBar");
        toolBar.setTitle("Views");
        toolBar.addDockableBarListener(new ToolBarListener());

        ToolViewDescriptor[] toolViewDescriptors = VisatActivator.getInstance().getToolViewDescriptors();
        List<String> viewCommandIdList = new ArrayList<String>(5);
        for (ToolViewDescriptor toolViewDescriptor : toolViewDescriptors) {
            String id = toolViewDescriptor.getId();
            if (!id.equals("org.esa.beam.visat.toolviews.stat.StatisticsToolView") &&
                !id.equals("org.esa.beam.visat.toolviews.pin.PinManagerToolView") &&
                !id.equals("org.esa.beam.visat.toolviews.pin.GcpManagerToolView") &&
                !id.equals("org.esa.beam.visat.toolviews.roi.RoiManagerToolView") &&
                !id.equals("org.esa.beam.visat.toolviews.bitmask.BitmaskOverlayToolView") ) {
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
                "openRaster",
                "save",
                null,
                "newProject",
                "loadProject",
                null,
                "properties",
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
        menuBar.add(createJMenu("geometry", "Geometry", 'G')); /*I18N*/
        menuBar.add(createJMenu("insar", "InSAR", 'I')); /*I18N*/
        menuBar.add(createJMenu("exploitation", "Exploitation", 'E')); /*I18N*/
        menuBar.add(createJMenu("window", "Window", 'W')); /*I18N*/
        menuBar.add(createJMenu("help", "Help", 'H')); /*I18N*/

        return menuBar;
    }

    @Override
    protected CommandBar createAnalysisToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("analysisToolBar");
        toolBar.setTitle("Processors");
        toolBar.addDockableBarListener(new ToolBarListener());

        addCommandsToToolBar(toolBar, new String[]{
                "openGraphBuilderDialog",
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
                //"selectTool",
                "crossHairTool",
                "rangeFinder",
                "zoomTool",
                "pannerTool",
                null,
                "pinTool",
                "org.esa.beam.visat.toolviews.pin.PinManagerToolView.showCmd",
                "gcpTool",
                "org.esa.beam.visat.toolviews.pin.GcpManagerToolView.showCmd",
                null,
                //"drawLineTool",
                "drawRectangleTool",
                "drawEllipseTool",
                //"drawPolylineTool",
                "drawPolygonTool",
                //"deleteShape",
//                "magicStickTool",
                null,
                "convertShapeToROI",
                "org.esa.beam.visat.toolviews.roi.RoiManagerToolView.showCmd",
                "org.esa.beam.visat.toolviews.bitmask.BitmaskOverlayToolView.showCmd",
                //"convertROIToShape",
        });

        return toolBar;
    }
}