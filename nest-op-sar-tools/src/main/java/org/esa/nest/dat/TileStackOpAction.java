package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * TileStack action.
 *
 */
public class TileStackOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("TileStackOp", getAppContext(), "TileStackOp", getHelpId());
            dialog.setTargetProductNameSuffix("_Stack");
        }
        dialog.show();

    }
}