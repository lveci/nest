package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

public class CoregistrationAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {
        CoregistrationDialog dialog = new CoregistrationDialog(getAppContext(), "Coregistration", "Coregistration");
        dialog.setTargetProductNameSuffix("_coreg");
        dialog.show();
    }

}