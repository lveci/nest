package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

public class SARSimTerrainCorrectionAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {
        SARSimTerrainCorrectionDialog dialog = new SARSimTerrainCorrectionDialog(getAppContext(),
                "SAR Sim Terrain Correction", "Orthorectifying-product");
        dialog.setTargetProductNameSuffix("_TC");
        dialog.show();
    }

}