
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;

/**
 * This does nothing
 *
 * @author lveci
 * @version $Revision: 1.2 $ $Date: 2010-01-04 14:23:42 $
 */
class ComingSoonAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(false);
    }
}