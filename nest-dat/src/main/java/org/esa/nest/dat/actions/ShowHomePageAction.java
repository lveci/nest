package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;

import java.awt.*;
import java.io.IOException;
import java.net.URI;

/**
 * This action lauchches the default browser to display the NEST Wiki
 * web page.
 *
 */
public class ShowHomePageAction extends ExecCommand {
    private static final String HOME_PAGE_URL_DEFAULT = "http://www.array.ca/nest/";

    /**
     * Launches the default browser to display the NEST Wiki.
     * Invoked when a command action is performed.
     *
     * @param event the command event.
     */
    @Override
    public void actionPerformed(CommandEvent event) {
        final String homePageUrl = System.getProperty("nest.homePageUrl", HOME_PAGE_URL_DEFAULT);
        final Desktop desktop = Desktop.getDesktop();

        try {
            desktop.browse(URI.create(homePageUrl));
        } catch (IOException e) {
            // TODO - handle
        } catch (UnsupportedOperationException e) {
            // TODO - handle
        }
    }
}
