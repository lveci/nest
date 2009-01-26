package org.esa.nest.dat;

import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.dat.dialogs.MultiGraphDialog;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;

import java.io.File;

/**
 *  Provides the User Interface for PCA
 */
public class PCADialog extends MultiGraphDialog {

    private final static String homeUrl = System.getProperty("nest.home", ".");
    private final static File graphPath = new File(homeUrl, File.separator + "graphs" + File.separator + "internal");
    private final static String internalFormat = DimapProductConstants.DIMAP_FORMAT_NAME;

    private final static File tmpFolder = new File("c:\\data");
    //private final static File tmpFile1 = new File(tmpFolder, "tmp1.dim");
    //private final static File tmpDataFile1 = new File(tmpFolder, "tmp1.dim.data");

    public PCADialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext, title, helpID);

    }

    @Override
    protected void createGraphs() throws GraphException {
        try {
            addGraph(new File(graphPath, "PCAStatsGraph.xml"), "PCA", true);
            addGraph(new File(graphPath, "PCAMinGraph.xml"), "", false);
            addGraph(new File(graphPath, "PCAImageGraph.xml"), "", false);

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
                tabbedPane.addTab(title, null,
                        n.GetOperatorUI().CreateOpTab(n.getOperatorName(), n.getParameterMap(), appContext),
                        n.getID() + " Operator");
            }
        }

        graphExecuterList.add(graphEx);
    }

    protected void assignParameters() throws GraphException {
            // first Graph - PCA-Statistics
            setIO(graphExecuterList.get(0),
                    "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath());

            // second Graph - PCA-Min
            setIO(graphExecuterList.get(1),
                    "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath());

            // third Graph - PCA-Image
            setIO(graphExecuterList.get(2),
                    "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath(),
                    "3-Write", getTargetFile().getAbsolutePath(), getTargetFormat());
    }

    private static void setIO(final GraphExecuter graphEx, final String readID, final String readPath) throws GraphException {
        setIO(graphEx, readID, readPath, null, null, null);
     }

    private static void setIO(final GraphExecuter graphEx,
                       final String readID, final String readPath,
                       final String writeID, final String writePath,
                       final String format) {
        final GraphNode readNode = graphEx.findGraphNode(readID);
        if (readNode != null) {
            graphEx.setOperatorParam(readNode.getID(), "file", readPath);
        }

        if (writeID != null) {
            final GraphNode writeNode = graphEx.findGraphNode(writeID);
            if (writeNode != null) {
                graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
                graphEx.setOperatorParam(writeNode.getID(), "file", writePath);
            }
        }
    }

    @Override
    protected void cleanUpTempFiles() {
        //tmpFile1.delete();
        //tmpDataFile1.delete();
    }
}