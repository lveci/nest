package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.beam.visat.VisatApp;

/**
 * MultilookOp action.
 *
 */
public class MultilookOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        VisatApp.getApp().showWarningDialog("Currently with simple multilooking, the detection for complex data is performed without any resampling");
        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Multilook", getAppContext(), "Multilook", getHelpId());
            dialog.setTargetProductNameSuffix("_ML");
        }
        dialog.show();

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(true);
    }
}