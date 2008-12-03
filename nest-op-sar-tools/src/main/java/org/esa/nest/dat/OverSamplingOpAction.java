package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * OverSamplingOp action.
 *
 */
public class OverSamplingOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Oversample", getAppContext(), "Oversample", getHelpId());
            dialog.setTargetProductNameSuffix("_OvrS");
        }
        dialog.show();

    }

}