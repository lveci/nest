package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

public class OrthorectificationAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {
        OrthorectificationDialog dialog = new OrthorectificationDialog (getAppContext(), "Orthorectification", "Orthorectifying-product");
        dialog.setTargetProductNameSuffix("_TC");
        dialog.show();
    }

}