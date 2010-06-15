package org.esa.nest.doris.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.DatContext;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

/**
 * Create Stack Doris Action.
 *
 */
public class CreateExperiment_1Action extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""),
                "Experiment 1", "Experiment_1Op", false);
        dialog.show();

//        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
//        final File graphFile =  new File(graphPath, "CreateStackDorisGraph.xml");
//        dialog.LoadGraph(graphFile);
    }
}