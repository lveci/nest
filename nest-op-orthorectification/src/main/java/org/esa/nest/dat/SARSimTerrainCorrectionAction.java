package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.gpf.SARSimTerrainCorrectionOp;

public class SARSimTerrainCorrectionAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {
        SARSimTerrainCorrectionDialog dialog = new SARSimTerrainCorrectionDialog(getAppContext(),
                "SAR Sim Terrain Correction", "SARSimGeocodingOp");
        dialog.setTargetProductNameSuffix(SARSimTerrainCorrectionOp.PRODUCT_SUFFIX);
        dialog.show();
    }

}