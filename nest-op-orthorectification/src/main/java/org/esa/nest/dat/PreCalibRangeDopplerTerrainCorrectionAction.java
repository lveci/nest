package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;

/**
 * PreCalibration-Range-Doppler-Terrain Correction action.
 *
 */
public class PreCalibRangeDopplerTerrainCorrectionAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(getAppContext(), "Pre-Calibration Range-Doppler Terrain Correction", "RangeDopplerGeocodingOp", false);
        dialog.show();

        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
        final File graphFile =  new File(graphPath, "PreCalibration_Orthorectify.xml");

        dialog.LoadGraph(graphFile);
    }
}