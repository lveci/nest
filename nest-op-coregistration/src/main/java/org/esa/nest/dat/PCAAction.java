package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

public class PCAAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {
        PCADialog dialog = new PCADialog(getAppContext(), "Principle Component Analysis", "PCAOp");
        dialog.setTargetProductNameSuffix("_PCA");
        dialog.show();
    }

}