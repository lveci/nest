
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;

/**
 * This action opens a vector dataset
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2008-08-26 17:00:57 $
 */
public class OpenVectorAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {


    }

     @Override
    public void updateState(final CommandEvent event) {

        setEnabled(false);
    }
}