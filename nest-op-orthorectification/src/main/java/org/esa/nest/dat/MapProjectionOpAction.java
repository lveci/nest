package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Map Projection action.
 *
 */
public class MapProjectionOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog(
                    "MapProjection", getAppContext(), "MapProjection", getHelpId());
            dialog.setTargetProductNameSuffix("_proj");
        }
        dialog.show();

    }
}