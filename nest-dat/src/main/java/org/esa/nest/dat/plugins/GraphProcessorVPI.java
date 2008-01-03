package org.esa.nest.dat.plugins;

import org.esa.beam.framework.ui.command.CommandAdapter;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.CommandManager;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.AbstractVisatPlugIn;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;

public class GraphProcessorVPI extends AbstractVisatPlugIn {

    private GraphProcessorDialog _gpDialog;
    private JFrame _pgFrame;

    /**
     * Called by VISAT after the plug-in instance has been registered in VISAT's plug-in manager.
     *
     * @param visatApp a reference to the VISAT application instance.
     */
    public void start(final VisatApp visatApp) {

        CommandAdapter runGraphAction = new CommandAdapter() {
            @Override
            public void actionPerformed(CommandEvent event) {

                if (_pgFrame == null) {
                    if(_gpDialog == null) {
                        _gpDialog = new GraphProcessorDialog();
                    }
                    _pgFrame = _gpDialog.getFrame();
                    _pgFrame.setIconImage(visatApp.getMainFrame().getIconImage());
                }
                _pgFrame.setVisible(true);
            }
        };
        CommandManager commandManager = visatApp.getCommandManager();
        ExecCommand runGraphCommand = commandManager.createExecCommand("runGraphProcessing", runGraphAction);
        runGraphCommand.setText("Graph Processing");
        runGraphCommand.setShortDescription("Starts Graph Processing");
        runGraphCommand.setParent("tools");

    }

    /**
     * Tells a plug-in to update its component tree (if any) since the Java look-and-feel has changed.
     * <p/>
     * <p>If a plug-in uses top-level containers such as dialogs or frames, implementors of this method should invoke
     * <code>SwingUtilities.updateComponentTreeUI()</code> on such containers.
     */
    @Override
    public void updateComponentTreeUI() {
        if (_pgFrame != null) {
            SwingUtilities.updateComponentTreeUI(_pgFrame);
        }
    }

}
