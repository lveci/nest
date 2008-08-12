package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * AmplitudeToIntensityOp action.
 *
 */
public class AmplitudeToIntensityOpAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Convert-Amplitude-Intensity", getAppContext(),
                    "Convert-Amplitude-Intensity", getHelpId());
        }
        dialog.show();

    }

}