package org.esa.nest.dat.oceantools;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Create Land Mask action.
 *
 */
public class CreateLandMaskOpAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final NestSingleTargetProductDialog dialog = new NestSingleTargetProductDialog("Create-LandMask", 
                getAppContext(), "Create-LandMask", getHelpId());
        dialog.setTargetProductNameSuffix("_msk");
        dialog.show();

    }

}