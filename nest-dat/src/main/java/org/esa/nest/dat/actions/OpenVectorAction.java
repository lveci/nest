
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.VisatActivator;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.toolviews.layermanager.LayerSourceDescriptor;
import org.esa.beam.visat.toolviews.layermanager.layersrc.LayerSourceAssistantPane;
import org.esa.beam.visat.toolviews.layermanager.layersrc.SelectLayerSourceAssistantPage;

/**
 * This action opens a vector dataset
 *
 * @author lveci
 * @version $Revision: 1.3 $ $Date: 2009-06-25 17:24:03 $
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