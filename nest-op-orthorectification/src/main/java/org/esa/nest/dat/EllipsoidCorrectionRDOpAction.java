package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Ellipsoid-Correction-RD action.
 *
 */
public class EllipsoidCorrectionRDOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog(
                    "Ellipsoid-Correction-RD", getAppContext(), "Ellipsoid-Correction-RD", getHelpId());
            dialog.setTargetProductNameSuffix("_EC");
        }
        dialog.show();

    }
}