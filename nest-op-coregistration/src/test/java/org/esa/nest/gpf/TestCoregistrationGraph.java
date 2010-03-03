package org.esa.nest.gpf;

import junit.framework.TestCase;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.beam.framework.gpf.graph.GraphException;
import com.bc.ceres.core.ProgressMonitor;

import java.io.File;


/**
 * Unit test for Coregistration Graph
 */
public class TestCoregistrationGraph extends TestCase {


    @Override
    protected void setUp() throws Exception {

    }

    @Override
    protected void tearDown() throws Exception {

    }

    public void testCreateGraph() throws GraphException {
      /*  final GraphExecuter graphEx = new GraphExecuter();
        graphEx.loadGraph(graphFile, true);

        graphEx.InitGraph();

        graphEx.executeGraph(ProgressMonitor.NULL);   */
    }

    private static void setIO(final GraphExecuter graphEx,
                              final String readID, final File readPath,
                              final String writeID, final File writePath,
                              final String format) {
        final GraphNode readNode = graphEx.findGraphNodeByOperator(readID);
        if (readNode != null) {
            graphEx.setOperatorParam(readNode.getID(), "file", readPath.getAbsolutePath());
        }

        if (writeID != null) {
            final GraphNode writeNode = graphEx.findGraphNodeByOperator(writeID);
            if (writeNode != null) {
                graphEx.setOperatorParam(writeNode.getID(), "formatName", format);
                graphEx.setOperatorParam(writeNode.getID(), "file", writePath.getAbsolutePath());
            }
        }
    }

}