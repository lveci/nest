package org.esa.nest.dat.oceantools;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;

/**
 * Oil Spill Detection action.
 *
 */
public class OilSpillDetectionOpAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(getAppContext(), "Oil-Spill-Detection", "Oil-Spill-Detection", false);
        dialog.show();

        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
        final File graphFile =  new File(graphPath, "OilSpillDetectionGraph.xml");

        dialog.LoadGraph(graphFile);
    }

}