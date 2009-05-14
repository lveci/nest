
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.toolviews.layermanager.layersrc.LayerSourceAssistantPane;
import org.esa.beam.visat.toolviews.layermanager.layersrc.SelectLayerSourceAssistantPage;
import org.esa.beam.visat.toolviews.layermanager.LayerSourceDescriptor;
import org.esa.beam.visat.VisatActivator;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;

import javax.swing.*;

/**
 * This action opens a vector dataset
 *
 * @author lveci
 * @version $Revision: 1.2 $ $Date: 2009-05-14 18:20:01 $
 */
public class OpenVectorAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {

        LayerSourceAssistantPane pane = new LayerSourceAssistantPane(VisatApp.getApp().getApplicationWindow(),
                "Add Layer",
                getAppContext());
        LayerSourceDescriptor[] layerSourceDescriptors = VisatActivator.getInstance().getLayerSources();
        pane.show(new SelectLayerSourceAssistantPage(layerSourceDescriptors));
    }

     @Override
    public void updateState(final CommandEvent event) {
        event.getCommand().setEnabled(VisatApp.getApp().getSelectedProductSceneView() != null);
    }
}