package org.esa.nest.dat.actions;

import com.jidesoft.swing.LayoutPersistence;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.util.ResourceUtils;

import java.io.File;

/**
 * User: Marco Peters
* Date: 11.06.2008
*/
public class LoadSideBySideLayoutAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {
        final LayoutPersistence layoutPersistence = VisatApp.getApp().getMainFrame().getLayoutPersistence();

        String layoutPath = ResourceUtils.getHomeUrl() + File.separator + "res" + File.separator + "sidebyside.layout";
        layoutPersistence.loadLayoutDataFromFile(layoutPath);
    }

}