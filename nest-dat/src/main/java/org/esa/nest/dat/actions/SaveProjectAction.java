
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.DatProject;

/**
 * This action saves the project and asks the user for the new file location.
 *
 * @author lveci
 * @version $Revision: 1.4 $ $Date: 2008-04-21 16:36:57 $
 */
public class SaveProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        DatProject.instance().SaveProject();

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(VisatApp.getApp().getProductManager().getNumProducts() > 0);
    }
}
