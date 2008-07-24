package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.VisatApp;

/**
 * Backward-Terrain-Correction action.
 *
 */
public class BackwardTerrainCorrectionOpAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Backward-Terrain-Correction", getAppContext(), "Backward-Terrain-Correction", null);
        }
        dialog.show();

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(false);
    }
}