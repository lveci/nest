package org.jdoris.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;
import org.jdoris.nest.gpf.ComplexSRDOp;

public class ComplexSRDAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        NestSingleTargetProductDialog dialog = new NestSingleTargetProductDialog("ComplexSRD", getAppContext(), "ComplexSRD", getHelpId());
        dialog.setTargetProductNameSuffix(ComplexSRDOp.PRODUCT_TAG);
        dialog.show();

//        final GraphBuilderDialog dialog = new GraphBuilderDialog(new DatContext(""), "TOPO phase computation and subtraction", "ComplexSRDOp", false);
//        dialog.show();
//
//        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
//        final File graphFile = new File(graphPath, "ComplexSRDGraph.xml");
//
//        dialog.LoadGraph(graphFile);
    }
}
