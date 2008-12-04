package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;

import java.awt.*;
import java.net.URI;
import java.io.IOException;

/**
 * This action emails a problem to Array
 *
 */
public class RequestFeatureAction extends ExecCommand {
    private static final String PR_EMAIL = "mailto:nest_pr@array.ca";

    /**
     * Invoked when a command action is performed.
     *
     * @param event the command event.
     */
    @Override
    public void actionPerformed(CommandEvent event) {

        final Desktop desktop = Desktop.getDesktop();
        final String mail = PR_EMAIL + "?subject=NEST-Feature-Request";

        try {
            desktop.mail(URI.create(mail));
        } catch (IOException e) {
            // TODO - handle
        } catch (UnsupportedOperationException e) {
            // TODO - handle
        }
    }
}