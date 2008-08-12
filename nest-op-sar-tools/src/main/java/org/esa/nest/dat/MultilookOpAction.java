package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.VisatApp;

/**
 * ASAR Calibration action.
 *
 */
public class MultilookOpAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Multilook", getAppContext(), "Multilook", getHelpId());
        }
        dialog.show();

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(false);
    }
}