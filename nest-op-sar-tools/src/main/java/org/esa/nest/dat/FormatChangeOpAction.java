package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * FormatChange action.
 *
 */
public class FormatChangeOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("Convert-Datatype", getAppContext(), "Convert-Datatype", getHelpId());
            dialog.setTargetProductNameSuffix("_DC");
        }
        dialog.show();

    }

}