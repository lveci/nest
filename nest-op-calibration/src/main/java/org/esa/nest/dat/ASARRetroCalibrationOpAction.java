package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * ASAR Retro-Calibration action.
 *
 */
public class ASARRetroCalibrationOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("ASAR-RetroCalibration", getAppContext(), "ASAR-RetroCalibration", getHelpId());
            dialog.setTargetProductNameSuffix("_RetroCalib");
        }
        dialog.show();

    }
}