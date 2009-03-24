package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;

/**
 * Forward-Terrain-Correction action.
 *
 */
public class ForwardTerrainCorrectionOpAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""), "Forward Terrain Correction",
                                                                "ForwardTerrainCorrection", false);
        dialog.show();

        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
        final File graphFile =  new File(graphPath, "ForwardTerrainCorrectionGraph.xml");

        dialog.LoadGraph(graphFile);
    }
}