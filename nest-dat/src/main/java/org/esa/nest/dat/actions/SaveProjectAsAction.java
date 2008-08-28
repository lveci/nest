
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.nest.dat.toolviews.Projects.Project;

/**
 * This action opens a project.
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2008-08-28 17:04:04 $
 */
public class SaveProjectAsAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        Project.instance().SaveProjectAs();
    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(Project.instance().IsProjectOpen());
    }
}