
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.nest.dat.DatProject;

import org.w3c.dom.*;

/**
 * This action saves the project and asks the user for the new file location.
 *
 * @author lveci
 * @version $Revision: 1.2 $ $Date: 2008-01-29 21:48:40 $
 */
public class SaveProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        DatProject.instance().SaveProject();

    }

}
