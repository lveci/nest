package org.esa.nest.dat.actions;

import com.jidesoft.swing.LayoutPersistence;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.framework.ui.command.CommandEvent;

import java.io.File;

/**
*/
public class LoadTabbedLayoutAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {
        loadTabbedLayout();    
    }

    public static void loadTabbedLayout() {
        final LayoutPersistence layoutPersistence = VisatApp.getApp().getMainFrame().getLayoutPersistence();

        final String homeUrl = System.getProperty("nest.home", ".");
        String layoutPath = homeUrl + File.separator + "res" + File.separator + "tabbed.layout";
        layoutPersistence.loadLayoutDataFromFile(layoutPath);
    }
}