package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * ApplyOrbitFileOp action.
 *
 */
public class ApplyOrbitFileOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Apply-Orbit-File", getAppContext(), "Apply Orbit File", getHelpId());
            dialog.setTargetProductNameSuffix("_AppOrb");
        }
        dialog.show();

    }
}