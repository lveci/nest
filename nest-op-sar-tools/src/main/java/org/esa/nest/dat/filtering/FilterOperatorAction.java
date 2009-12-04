package org.esa.nest.dat.filtering;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * Filter operator action.
 *
 */
public class FilterOperatorAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("Image-Filter", getAppContext(), "Image Filter", getHelpId());
            dialog.setTargetProductNameSuffix("_Filt");
        }
        dialog.show();

    }
}