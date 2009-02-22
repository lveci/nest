package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * CreateCoherenceImageOp action.
 *
 */
public class CreateCoherenceImageOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Create-Coherence-Image", getAppContext(), "Create Coherence Image", getHelpId());
            dialog.setTargetProductNameSuffix("_CH");
        }
        dialog.show();

    }
}