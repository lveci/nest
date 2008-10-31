package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * UnderSamplingOp action.
 *
 */
public class UnderSamplingOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Undersample", getAppContext(), "Undersample", getHelpId());
            dialog.setTargetProductNameSuffix("_UndS");
        }
        dialog.show();

    }
}