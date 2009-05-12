package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Range-Doppler-Geocoding action.
 *
 */
public class RangeDopplerGeocodingOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog(
                    "Range-Doppler-Geocoding", getAppContext(), "Range-Doppler-Geocoding", getHelpId());
            dialog.setTargetProductNameSuffix("_TC");
        }
        dialog.show();

    }
}