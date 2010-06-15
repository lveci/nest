package org.esa.nest.doris.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.DatContext;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

/**
 * Created by IntelliJ IDEA.
 * User: pmar
 * Date: May 13, 2010
 * Time: 4:59:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class CreateCoregistrationResampleAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""),
                "Resampling", "CoregistrationResampleOp", false);
        dialog.show();

//        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
//        final File graphFile =  new File(graphPath, "CreateStackDorisGraph.xml");
//        dialog.LoadGraph(graphFile);

    }
}