package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * RemoveAntennaPattern action.
 *
 */
public class RemoveAntennaPatternOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("RemoveAntennaPattern", getAppContext(), "Remove Antenna Pattern", getHelpId());
            dialog.setTargetProductNameSuffix("_-AntPat");
        }
        dialog.show();
    }
}