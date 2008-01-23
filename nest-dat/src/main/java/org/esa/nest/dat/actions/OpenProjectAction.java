
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.SharedApp;

/**
 * This action opens a project.
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2008-01-23 19:51:56 $
 */
public class OpenProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {
        SharedApp.instance().getApp().openProduct(null);
    }
}
