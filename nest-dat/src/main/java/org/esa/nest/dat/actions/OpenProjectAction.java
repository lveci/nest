
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.nest.dat.DatProject;

/**
 * This action opens a project.
 *
 * @author lveci
 * @version $Revision: 1.2 $ $Date: 2008-01-29 21:48:40 $
 */
public class OpenProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        DatProject.instance().LoadProject();
    }
}
