package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * CreateElevationBandOp action.
 *
 */
public class CreateElevationBandOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("CreateElevationBand", getAppContext(), "Create Elevation Band", getHelpId());
        }
        dialog.show();
    }
}