package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;

/**
 * PreCalibration-Multilook-Range-Doppler-Terrain Correction action.
 *
 */
public class PreCalibMultilookRangeDopplerTerrainCorrectionAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(getAppContext(), "Pre-Calibration Multilook Range-Doppler Terrain Correction", "RangeDopplerGeocodingOp", false);
        dialog.show();

        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
        final File graphFile =  new File(graphPath, "PreCalibration_Multilook_Orthorectify.xml");

        dialog.LoadGraph(graphFile);
    }
}