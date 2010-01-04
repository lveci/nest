
package org.esa.nest.dat.actions;

import org.esa.beam.BeamUiActivator;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.layer.LayerSourceAssistantPane;
import org.esa.beam.framework.ui.layer.LayerSourceDescriptor;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.toolviews.layermanager.layersrc.SelectLayerSourceAssistantPage;

/**
 * This action opens a vector dataset
 *
 * @author lveci
 * @version $Revision: 1.5 $ $Date: 2010-01-04 14:23:42 $
 */
public class OpenVectorAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {

        final LayerSourceAssistantPane pane = new LayerSourceAssistantPane(VisatApp.getApp().getApplicationWindow(),
                "Add Layer",
                getAppContext());
        final LayerSourceDescriptor[] layerSourceDescriptors = BeamUiActivator.getInstance().getLayerSources();
        pane.show(new SelectLayerSourceAssistantPage(layerSourceDescriptors));
    }

     @Override
    public void updateState(final CommandEvent event) {
        event.getCommand().setEnabled(VisatApp.getApp().getSelectedProductSceneView() != null);
    }
}