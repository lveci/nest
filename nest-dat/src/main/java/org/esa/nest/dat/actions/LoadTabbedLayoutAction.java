package org.esa.nest.dat.actions;

import com.jidesoft.swing.LayoutPersistence;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.framework.ui.command.CommandEvent;

/**
 * User: Marco Peters
* Date: 11.06.2008
*/
public class LoadTabbedLayoutAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {
        final LayoutPersistence layoutPersistence = VisatApp.getApp().getMainFrame().getLayoutPersistence();

        final String homeUrl = System.getProperty("nest.home", ".");
        String layoutPath = homeUrl + "\\res\\" + "tabbed.layout";
        layoutPersistence.loadLayoutDataFromFile(layoutPath);
    }

}