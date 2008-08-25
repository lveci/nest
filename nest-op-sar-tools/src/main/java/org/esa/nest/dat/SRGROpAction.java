package org.esa.nest.dat;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * SRGROp action.
 *
 */
public class SRGROpAction extends AbstractVisatAction {

    private ModelessDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("SRGR", getAppContext(), "Slant Range to Ground Range", getHelpId());
        }
        dialog.show();

    }

}