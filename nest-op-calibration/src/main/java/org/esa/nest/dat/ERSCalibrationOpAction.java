package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * ERS Calibration action.
 *
 */
public class ERSCalibrationOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("ERS-Calibration", getAppContext(), "ERS Calibration", getHelpId());
            dialog.setTargetProductNameSuffix("_Calib");
        }
        dialog.show();

    }
}