package org.esa.nest.dat.plugins;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.graph.Graph;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.gpf.graph.GraphProcessor;
import org.esa.beam.framework.ui.command.CommandAdapter;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.CommandManager;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.visat.AbstractVisatPlugIn;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import java.util.Set;

public class GraphProcessorVPI extends AbstractVisatPlugIn {

    private GPF gpf;
    private static final String MESSAGE_BOX_TITLE = "Graph Processing";  /*I18N*/

    protected void initialize() {

        gpf = GPF.getDefaultInstance();
        gpf.getOperatorSpiRegistry().loadOperatorSpis();
    }

    /**
     * Called by VISAT after the plug-in instance has been registered in VISAT's plug-in manager.
     *
     * @param visatApp a reference to the VISAT application instance.
     */
    public void start(VisatApp visatApp) {

        initialize();

        CommandAdapter runGraphAction = new CommandAdapter() {
            @Override
            public void actionPerformed(CommandEvent event) {

                run();
            }
        };
        CommandManager commandManager = visatApp.getCommandManager();
        ExecCommand runGraphCommand = commandManager.createExecCommand("runGraphProcessing", runGraphAction);
        runGraphCommand.setText("Graph Processing");
        runGraphCommand.setShortDescription("Starts Graph Processing");
        runGraphCommand.setParent("tools");
        //runGraphCommand.setPlaceAfter("showUpdateDialog");
        //runGraphCommand.setPlaceBefore("about");


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

    private static void run() {
        final SwingWorker swingWorker = new SwingWorker<Integer, Integer>() {
            @Override
            protected Integer doInBackground() throws Exception {

                return 0;
            }

            @Override
            public void done() {

            }
        };
        swingWorker.execute();
    }

    Set GetOperatorList() {

        return gpf.getOperatorSpiRegistry().getAliases();
    }


    public void executeGraph(Graph graph) throws GraphException {
        GraphProcessor processor = new GraphProcessor();
        processor.executeGraph(graph, ProgressMonitor.NULL);
    }

}
