package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * GeographicToSatellite action.
 *
 */
public class GeographicToSatelliteOpAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

       /* if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("FocusingAdjustment", getAppContext(), "FocusingAdjustment", getHelpId());
        }
        dialog.show();   */

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(false);
    }
}