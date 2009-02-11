package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;

/**
 * SLC to PRI action.
 *
 */
public class SLCtoPRIAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""), "SLC to PRI", "SLCtoPRI", false);
        dialog.show();

        final String homeUrl = System.getProperty("nest.home", ".");
        final File graphPath = new File(homeUrl, File.separator + "graphs" + File.separator + "internal");
        final File SLCtoPRIFile =  new File(graphPath, "SLCtoPRIGraph.xml");

        dialog.LoadGraph(SLCtoPRIFile);
    }
}