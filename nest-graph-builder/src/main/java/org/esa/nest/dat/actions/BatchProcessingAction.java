package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.dialogs.BatchGraphDialog;

public class BatchProcessingAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(final CommandEvent event) {
        final BatchGraphDialog dialog = new BatchGraphDialog(getAppContext(), "Batch Processing", "batch_processing");
        dialog.show();
    }

}