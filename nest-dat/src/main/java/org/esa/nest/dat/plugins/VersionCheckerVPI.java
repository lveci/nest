/*
 * $Id: VersionCheckerVPI.java,v 1.3 2009-05-12 16:21:01 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package org.esa.nest.dat.plugins;

import com.bc.ceres.swing.UriLabel;
import org.esa.beam.framework.ui.command.CommandAdapter;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.CommandManager;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.util.VersionChecker;
import org.esa.beam.visat.AbstractVisatPlugIn;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.ResourceUtils;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;

public class VersionCheckerVPI extends AbstractVisatPlugIn {

    private static final String MESSAGE_BOX_TITLE = "Software Version Check";  /*I18N*/
    private static final int DELAY_MILLIS = 5 * 1000;  // 5 seconds delay

    private static final String remoteVersionUrl = "http://www.array.ca/nest/version.txt";
    private static final String NEST_WEBSITE = "http://www.array.ca/nest";

    private static final String DISABLE_HINT = "Please note that you can disable the on-line version check\n" +
            "in the preferences dialog.";

    /**
     * Called by VISAT after the plug-in instance has been registered in VISAT's plug-in manager.
     *
     * @param visatApp a reference to the VISAT application instance.
     */
    public void start(VisatApp visatApp) {
        if (!isVersionCheckQuestionSuppressed() || isVersionCheckEnabled()) {
            final Timer timer = new Timer(DELAY_MILLIS, new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    runAuto();
                }
            });
            timer.setRepeats(false);
            timer.start();
        }

        CommandAdapter versinoCheckerAction = new CommandAdapter() {
            @Override
            public void actionPerformed(CommandEvent event) {
                runManual();
            }
        };
        CommandManager commandManager = visatApp.getCommandManager();
        ExecCommand versionCheckerCommand = commandManager.createExecCommand("checkForUpdate", versinoCheckerAction);
        versionCheckerCommand.setText("Check for New Release...");
        versionCheckerCommand.setShortDescription("Checks for a new "+VisatApp.getApp().getAppName()+" release");
        versionCheckerCommand.setParent("help");
        versionCheckerCommand.setPlaceAfter("showUpdateDialog");
        versionCheckerCommand.setPlaceBefore("about");
    }

    /**
     * Tells a plug-in to update its component tree (if any) since the Java look-and-feel has changed.
     * <p/>
     * <p>If a plug-in uses top-level containers such as dialogs or frames, implementors of this method should invoke
     * <code>SwingUtilities.updateComponentTreeUI()</code> on such containers.
     */
    @Override
    public void updateComponentTreeUI() {
    }

    private static void runManual() {
        run(false, true);
    }

    private static void runAuto() {
        final boolean prompt = !isVersionCheckQuestionSuppressed();
        if (isVersionCheckEnabled()) {
            run(true, prompt);
        }
    }

    private static void run(final boolean auto, final boolean prompt) {
        final SwingWorker swingWorker = new SwingWorker<Integer, Integer>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return new Integer(getVersionStatus());
            }

            @Override
            public void done() {
                try {
                    showVersionStatus(auto, prompt, get().intValue());
                } catch (InterruptedException e) {
                    showVersionCheckFailedMessage(auto, prompt, e);
                } catch (ExecutionException e) {
                    showVersionCheckFailedMessage(auto, prompt, e.getCause());

                }
            }
        };
        swingWorker.execute();
    }

    private static int getVersionStatus() throws IOException {
        final VersionChecker versionChecker = new VersionChecker();
        versionChecker.setLocalVersionFile(new File(ResourceUtils.findHomeFolder(), "VERSION.txt"));
        versionChecker.setRemoteVersionUrlString(remoteVersionUrl);
        VisatApp.getApp().getLogger().info(
                "comparing local software version with the one from " + versionChecker.getRemoteVersionUrlString());
        return versionChecker.compareVersions();
    }

    private static void showVersionStatus(boolean auto, boolean prompt, int versionStatus) {
        if (versionStatus < 0) {
            showOutOfDateMessage(auto);
        } else {
            showUpToDateMessage(auto, prompt);
        }
    }

    private static void showVersionCheckPrompt() {
        final String message = MessageFormat.format("{0} is about to check for a new software version.\n" +
                "Do you want {0} to perform the on-line version check now?", /*I18N*/
                                                                             VisatApp.getApp().getAppName());
        VisatApp.getApp().showQuestionDialog(message, VisatApp.PROPERTY_KEY_VERSION_CHECK_ENABLED);
    }

    private static void showOutOfDateMessage(boolean auto) {
        VisatApp.getApp().getLogger().info("version check performed, application is antiquated");
        JLabel beamHomeLabel;
        try {
            beamHomeLabel = new UriLabel(new URI(NEST_WEBSITE));
        } catch (URISyntaxException e) {
            beamHomeLabel = new JLabel(NEST_WEBSITE);
            beamHomeLabel.setForeground(Color.BLUE.darker());
        }
        Object[] message = new Object[]{
                "A new software version is available.\n" +
                        "Please visit the NEST homepage at\n", /*I18N*/
                beamHomeLabel,
                "to update your NEST software with the latest features and bug fixes.\n" + /*I18N*/
                        (auto ? "\n" + DISABLE_HINT : "")
        };
        JOptionPane.showMessageDialog(VisatApp.getApp().getMainFrame(),
                                      message,
                                      MESSAGE_BOX_TITLE,
                                      JOptionPane.INFORMATION_MESSAGE);
    }

    private static void showUpToDateMessage(boolean auto, boolean prompt) {
        VisatApp.getApp().getLogger().info("version check performed, application is up-to-date");
        if (prompt && !auto) {
            JOptionPane.showMessageDialog(VisatApp.getApp().getMainFrame(),
                                          "Your "+VisatApp.getApp().getAppName()+" software is up-to-date.\n" +  /*I18N*/
                                                  (auto ? "\n" + DISABLE_HINT : ""),
                                          MESSAGE_BOX_TITLE,
                                          JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void showVersionCheckFailedMessage(boolean auto, boolean prompt, Throwable t) {
        VisatApp.getApp().getLogger().severe("I/O error: " + t.getMessage());
        if (prompt && !auto) {
            JOptionPane.showMessageDialog(VisatApp.getApp().getMainFrame(),
                                          "The on-line version check failed,\n" +
                                                  "an I/O error occured.\n" + /*I18N*/
                                                  (auto ? "\n" + DISABLE_HINT : ""),
                                          MESSAGE_BOX_TITLE,
                                          JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean isVersionCheckEnabled() {
        return getPreferences().getPropertyBool(VisatApp.PROPERTY_KEY_VERSION_CHECK_ENABLED, true);
    }

    private static boolean isVersionCheckQuestionSuppressed() {
        return getPreferences().getPropertyBool(VisatApp.PROPERTY_KEY_VERSION_CHECK_DONT_ASK, false);
    }

    private static PropertyMap getPreferences() {
        return VisatApp.getApp().getPreferences();
    }
}