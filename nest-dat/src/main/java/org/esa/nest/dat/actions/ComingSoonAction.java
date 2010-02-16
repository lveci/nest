
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;

/**
 * This does nothing
 *
 * @author lveci
 * @version $Revision: 1.3 $ $Date: 2010-02-16 18:19:00 $
 */
public class ComingSoonAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

    }

    @Override
    public void updateState(final CommandEvent event) {
        setEnabled(false);
    }
}