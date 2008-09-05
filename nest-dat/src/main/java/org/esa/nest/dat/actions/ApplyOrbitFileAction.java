
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.dat.toolviews.Projects.Project;
import org.esa.nest.dataio.ApplyOrbitFile;

/**
 * This action to edit Metadata
 *
 * @author lveci
 * @version $Revision: 1.2 $ $Date: 2008-09-05 14:23:32 $
 */
public class ApplyOrbitFileAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        ApplyOrbitFile orb = new ApplyOrbitFile(VisatApp.getApp().getSelectedProduct());
        
    }

    @Override
    public void updateState(final CommandEvent event) {
        final int n = VisatApp.getApp().getProductManager().getProductCount();
        setEnabled(n > 0);
    }
}