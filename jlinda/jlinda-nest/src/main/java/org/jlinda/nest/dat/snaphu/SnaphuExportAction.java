package org.jlinda.nest.dat.snaphu;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;

public class SnaphuExportAction extends AbstractVisatAction {
    @Override
    public void actionPerformed(CommandEvent event) {
        final GraphBuilderDialog dialog = new GraphBuilderDialog(VisatApp.getApp(),
                "Snaphu Data Export", "SnaphuExportOp", false);
        dialog.show();

        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
        final File graphFile = new File(graphPath, "SnaphuExportGraph.xml");

        dialog.LoadGraph(graphFile);

    }
}
