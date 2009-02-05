package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * WSS Mosaic action.
 *
 */
public class WSSMosaicOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("WSS-Mosaic", getAppContext(), "WSS-Mosaic", getHelpId());
            dialog.setTargetProductNameSuffix("_mos");
        }
        dialog.show();
    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(false);
    }
}