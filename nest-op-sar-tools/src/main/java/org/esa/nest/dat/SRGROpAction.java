package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.NestSingleTargetProductDialog;

/**
 * SRGROp action.
 *
 */
public class SRGROpAction extends AbstractVisatAction {

    private NestSingleTargetProductDialog dialog = null;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new NestSingleTargetProductDialog("SRGR", getAppContext(), "Slant Range to Ground Range", getHelpId());
            dialog.setTargetProductNameSuffix("_GrdRg");
        }
        dialog.show();

    }

}