package org.esa.beam.gpf.operators.reproject;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Geographic collocation action.
 *
 * @author Ralf Quast
 * @version $Revision: 1.1 $ $Date: 2010-03-31 14:00:01 $
 */
public class ReprojectionAction extends AbstractVisatAction {
    
    private ModelessDialog dialog;

    @Override
    public void actionPerformed(CommandEvent event) {
        if (dialog == null) {
            dialog = new ReprojectionDialog(false, "Reprojection", event.getCommand().getHelpId(), getAppContext());
        }
        dialog.show();
    }
}
