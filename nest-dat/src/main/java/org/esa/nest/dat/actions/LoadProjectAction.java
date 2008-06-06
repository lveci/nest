
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.nest.dat.toolviews.Projects.Project;

/**
 * This action opens a project.
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2008-06-06 18:07:22 $
 */
public class LoadProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        Project.instance().LoadProject();
    }
}
