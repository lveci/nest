package org.esa.nest.dat;

import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.nest.dat.dialogs.MultiGraphDialog;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;

import java.io.File;

/**
 *  Provides the User Interface for PCA
 */
public class PCADialog extends MultiGraphDialog {

    private final static String homeUrl = System.getProperty("nest.home", ".");
    private final static File graphPath = new File(homeUrl, File.separator + "graphs");
    private final static String internalFormat = DimapProductConstants.DIMAP_FORMAT_NAME;

    private final static File tmpFolder = new File("c:\\data");

    public PCADialog(final AppContext theAppContext, final String title, final String helpID) {
        super(theAppContext, title, helpID);

    }

    protected void createGraphs() throws GraphException {

        // first Graph - PCA-Statistics
        final File graphFile1 =  new File(graphPath, "MultilookGraph.xml");
        final File tmpFile1 = new File(tmpFolder, "tmp1.dim");
        addGraph(graphFile1,
                "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath(),
                "3-Write", tmpFile1.getAbsolutePath(), internalFormat);

        // second Graph - PCA-Min
        final File graphFile2 =  new File(graphPath, "MultilookGraph.xml");
        addGraph(graphFile2,
                "1-Read", tmpFile1.getAbsolutePath(),
                "3-Write", getTargetFile().getAbsolutePath(), getTargetFormat());
    }

    private void addGraph(final File graphFile, final String readID, final String readPath,
                                                final String writeID, final String writePath,
                                                final String format) throws GraphException {
        final GraphExecuter graphEx = new GraphExecuter();

        LoadGraph(graphEx, graphFile);

        final GraphNode readNode = graphEx.findGraphNode(readID);
        graphEx.setOperatorParam(readNode.getID(), "file", readPath);

        final GraphNode writeNode = graphEx.findGraphNode(writeID);
        graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
        graphEx.setOperatorParam(writeNode.getID(), "file", writePath);

        //graphEx.recreateGraphContext();
        graphExecuterList.add(graphEx);
    }
}