
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.toolviews.Projects.Project;

/**
 * This action to edit Metadata
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2008-07-23 19:47:17 $
 */
public class EditMetadataAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        //Project.instance().LoadProject();
    }

    @Override
    public void updateState(final CommandEvent event) {
        final int n = VisatApp.getApp().getProductManager().getProductCount();
        setEnabled(n > 0);
    }
}