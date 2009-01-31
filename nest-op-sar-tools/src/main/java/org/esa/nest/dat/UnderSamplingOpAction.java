package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * UnderSamplingOp action.
 *
 */
public class UnderSamplingOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Undersample", getAppContext(), "Undersample", getHelpId());
            dialog.setTargetProductNameSuffix("_UndS");
        }
        dialog.show();

    }

}