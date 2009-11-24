package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * FillHoleOp action.
 *
 */
public class FillHoleOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Fill-Hole", getAppContext(), "Fill-Hole", getHelpId());
            dialog.setTargetProductNameSuffix("_FillHole");
        }
        dialog.show();

    }
}