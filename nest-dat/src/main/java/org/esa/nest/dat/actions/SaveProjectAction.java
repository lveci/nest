
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.SharedApp;
import org.esa.nest.dat.DatProject;

/**
 * This action saves the project and asks the user for the new file location.
 *
 * @author lveci
 * @version $Revision: 1.3 $ $Date: 2008-01-30 14:47:10 $
 */
public class SaveProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        DatProject.instance().SaveProject();

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(SharedApp.instance().getApp().getProductManager().getNumProducts() > 0);
    }
}
