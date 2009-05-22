package org.esa.nest.dat;

import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * WSS Deburst action.
 *
 * @author Ralf Quast
 * @version $Revision: 1.2 $ $Date: 2009-05-22 18:48:48 $
 */
public class WSSDeBurstOpAction extends AbstractVisatAction {

    private DefaultSingleTargetProductDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {

        if (dialog == null) {
            dialog = new DefaultSingleTargetProductDialog("WSS-Deburst", getAppContext(), "WSS-Deburst", getHelpId());
            dialog.setTargetProductNameSuffix("_Deb");
        }
        dialog.show();

    }
}