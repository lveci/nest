package org.esa.nest.dat;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.dat.dialogs.MultiGraphDialog;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.nest.util.ResourceUtils;

import java.awt.*;
import java.io.File;

/**
 *  Provides the User Interface for Automated Terrain Correction
 */
class SARSimTerrainCorrectionDialog extends MultiGraphDialog {

    private final static String homeUrl = System.getProperty("nest.home", ".");
    private final static File graphPath = new File(homeUrl, File.separator + "graphs" + File.separator + "internal");
    private final static String internalFormat = DimapProductConstants.DIMAP_FORMAT_NAME;

    private final static File tmpFolder = ResourceUtils.getApplicationUserTempDataDir();
    private final static File tmpFile1 = new File(tmpFolder, TMP_FILENAME + ".dim");
    private final static File tmpDataFile1 = new File(tmpFolder, TMP_FILENAME + ".data");

    public SARSimTerrainCorrectionDialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext, title, helpID, true);

        getIOPanel().setTargetProductName("gcp_selected_TC");
    }

    @Override
    protected void createGraphs() throws GraphException {
        try {
            addGraph(new File(graphPath, "SARSimulationGraph.xml"), "", true);
            addGraph(new File(graphPath, "SARSimTerrainCorrectionGraph.xml"), "", true);

            //placeTargetProductTabAtEnd();

        } catch(Exception e) {
            throw new GraphException(e.getMessage());
        }
    }

    private void addGraph(final File graphFile, final String title, final boolean addUI) {

        final GraphExecuter graphEx = new GraphExecuter();
        LoadGraph(graphEx, graphFile);

        for(GraphNode n : graphEx.GetGraphNodes()) {
            if(n.GetOperatorUI() == null)
                continue;
            if(n.getNode().getOperatorName().equals("Read") || n.getNode().getOperatorName().equals("Write")) {
                n.setOperatorUI(null);
                continue;
            }

            if(addUI) {
                String tabTitle = title;
                if(tabTitle.isEmpty())
                    tabTitle = n.getOperatorName();
                tabbedPane.addTab(tabTitle, null,
                        n.GetOperatorUI().CreateOpTab(n.getOperatorName(), n.getParameterMap(), appContext),
                        n.getID() + " Operator");
            }
        }

        graphExecuterList.add(graphEx);
    }

    private void placeTargetProductTabAtEnd() {
        final int index = tabbedPane.indexOfTab("Target Product");
        final Component tab = tabbedPane.getComponentAt(index);
        tabbedPane.remove(index);
        tabbedPane.add("Target Product", tab);
    }

    @Override
    protected void assignParameters() throws GraphException {
        
        // first Graph - SAR Simulation
        setIO(graphExecuterList.get(0),
                "1-Read", ioPanel.getSelectedSourceProduct().getFileLocation(),
                "4-Write", tmpFile1, internalFormat);

        // second Graph - Automated Terrain Correction
        setIO(graphExecuterList.get(1),
                "1-Read", tmpFile1,
                "3-Write", ioPanel.getTargetFile(), ioPanel.getTargetFormat());
    }

    private static void setIO(final GraphExecuter graphEx,
                              final String readID, final File readPath,
                              final String writeID, final File writePath,
                              final String format) {
        final GraphNode readNode = graphEx.findGraphNode(readID);
        if (readNode != null) {
            graphEx.setOperatorParam(readNode.getID(), "file", readPath.getAbsolutePath());
        }

        if (writeID != null) {
            final GraphNode writeNode = graphEx.findGraphNode(writeID);
            if (writeNode != null) {
                graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
                graphEx.setOperatorParam(writeNode.getID(), "file", writePath.getAbsolutePath());
            }
        }
    }

    @Override
    protected void cleanUpTempFiles() {
        tmpFile1.delete();
        ResourceUtils.deleteDir(tmpDataFile1);
    }
}