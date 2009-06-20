package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * SAR-Simulation action.
 *
 */
public class SARSimulationOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog(
                    "SAR-Simulation", getAppContext(), "SAR-Simulation", getHelpId());
            dialog.setTargetProductNameSuffix("_SIM");
        }
        dialog.show();

    }
}