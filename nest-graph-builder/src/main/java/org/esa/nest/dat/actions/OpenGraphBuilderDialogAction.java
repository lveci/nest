package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

public class OpenGraphBuilderDialogAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(final CommandEvent event) {
        //if (dialog == null) {
            dialog = new GraphBuilderDialog(getAppContext(), "Graph Builder", "graph_builder");
        //}
        dialog.show();
    }

}