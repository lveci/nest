package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Geolocation-Grid-Geocoding action.
 *
 */
public class GeolocationGridGeocodingOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog(
                    "Ellipsoid-Correction", getAppContext(), "Ellipsoid-Correction", getHelpId());
            dialog.setTargetProductNameSuffix("_EC");
        }
        dialog.show();

    }
}