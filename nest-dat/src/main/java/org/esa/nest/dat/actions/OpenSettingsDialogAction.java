package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.nest.dat.dialogs.SettingsDialog;

public class OpenSettingsDialogAction extends ExecCommand {

    private SettingsDialog dlg = null;

    @Override
    public void actionPerformed(final CommandEvent event) {
        //if(dlg == null) {
            dlg = new SettingsDialog("Settings");
        //}
        dlg.show();
    }

}