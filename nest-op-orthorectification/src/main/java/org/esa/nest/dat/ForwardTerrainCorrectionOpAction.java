package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Forward-Terrain-Correction action.
 *
 */
public class ForwardTerrainCorrectionOpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Forward-Terrain-Correction", getAppContext(),
                    "Forward-Terrain-Correction", getHelpId());
            dialog.setTargetProductNameSuffix("_OrthRect");
        }
        dialog.show();

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(true);
    }
}