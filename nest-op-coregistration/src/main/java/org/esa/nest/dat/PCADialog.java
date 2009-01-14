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
            // first Graph - PCA-Statistics
            final File graphFile1 =  new File(graphPath, "PCAStatsGraph.xml");
            addGraph(graphFile1, "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath());

            // second Graph - PCA-Min
            final File graphFile2 =  new File(graphPath, "PCAMinGraph.xml");
            addGraph(graphFile2, "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath());

            // third Graph - PCA-Image
            final File graphFile3 =  new File(graphPath, "PCAImageGraph.xml");
            addGraph(graphFile3,
                    "1-Read", getSelectedSourceProduct().getFileLocation().getAbsolutePath(),
                    "3-Write", getTargetFile().getAbsolutePath(), getTargetFormat());

        } catch(Exception e) {
            throw new GraphException(e.getMessage());
        }
    }

     private void addGraph(final File graphFile, final String readID, final String readPath) throws GraphException {
        addGraph(graphFile, readID, readPath, null, null, null);
     }

    private void addGraph(final File graphFile, final String readID, final String readPath,
                                                final String writeID, final String writePath,
                                                final String format) throws GraphException {
        try {
            final GraphExecuter graphEx = new GraphExecuter();
            LoadGraph(graphEx, graphFile);

            final GraphNode readNode = graphEx.findGraphNode(readID);
            if(readNode != null) {
                graphEx.setOperatorParam(readNode.getID(), "file", readPath);
            }

            if(writeID != null) {
                final GraphNode writeNode = graphEx.findGraphNode(writeID);
                if(writeNode != null) {
                    graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
                    graphEx.setOperatorParam(writeNode.getID(), "file", writePath);
                }
            }

            graphExecuterList.add(graphEx);
        } catch(Exception e) {
            throw new GraphException(e.getMessage());
        }
    }

    @Override
    protected void cleanUpTempFiles() {
        //tmpFile1.delete();
        //tmpDataFile1.delete();
    }
}