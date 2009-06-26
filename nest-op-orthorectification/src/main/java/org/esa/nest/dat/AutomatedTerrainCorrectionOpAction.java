package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Automated-Terrain-Correction action.
 *
 */
public class AutomatedTerrainCorrectionOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog(
                    "Automated-Terrain-Correction", getAppContext(), "Automated-Terrain-Correction", getHelpId());
            dialog.setTargetProductNameSuffix("_TC");
        }
        dialog.show();

    }
}