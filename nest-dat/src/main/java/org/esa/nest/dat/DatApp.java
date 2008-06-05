
package org.esa.nest.dat;

import com.jidesoft.action.CommandBar;
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
                "open",
                "save",
                null,
                "properties",
        });

        return toolBar;
    }

    protected CommandBar createAnalysisToolBar() {
        // context of action in module.xml used as key
        final CommandBar toolBar = new CommandBar("analysisToolBar");
        toolBar.setTitle("Analysis");
        toolBar.addDockableBarListener(new ToolBarListener());

        //addCommandsToToolBar(toolBar, new String[]{
        //        "openInformationDialog",
        //});

        return toolBar;
    }
}