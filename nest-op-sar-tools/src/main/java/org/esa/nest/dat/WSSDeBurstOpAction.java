package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * WSS Deburst action.
 *
 */
public class WSSDeBurstOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("DeburstWSS", getAppContext(), "WSS-Deburst", getHelpId());
            dialog.setTargetProductNameSuffix("_Deb");
        }
        dialog.show();

    }
}