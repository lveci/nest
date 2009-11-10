package org.esa.nest.dat.oceantools;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Wind Field Estimation action.
 *
 */
public class WindFieldEstimationOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Wind-Field-Estimation", getAppContext(), "Wind-Field-Estimation", getHelpId());
        }
        dialog.setTargetProductNameSuffix("_wind");
        dialog.show();

    }
}