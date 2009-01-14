package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

public class PCAAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(final CommandEvent event) {
        if (dialog == null) {
            dialog = new PCADialog(getAppContext(), "Principle Component Analysis", "PCA");
        }
        dialog.show();
    }

}