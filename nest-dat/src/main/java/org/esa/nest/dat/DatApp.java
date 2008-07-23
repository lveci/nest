
package org.esa.nest.dat;

import com.jidesoft.action.CommandBar;
import com.jidesoft.action.CommandMenuBar;
import org.esa.beam.framework.ui.*;
import org.esa.beam.framework.ui.application.ApplicationDescriptor;
import org.esa.beam.visat.*;

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
        menuBar.add(createJMenu("sartools", "SAR Tools", 'T')); /*I18N*/
        menuBar.add(createJMenu("geometry", "Geometry", 'T')); /*I18N*/
        menuBar.add(createJMenu("insar", "InSAR", 'T')); /*I18N*/
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
                "pinTool",
                "gcpTool",
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
                //"convertROIToShape",
        });

        return toolBar;
    }
}