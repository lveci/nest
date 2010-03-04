package org.esa.nest.gpf.oceantools;

import junit.framework.TestCase;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;
import org.esa.nest.util.TestUtils;
import org.esa.nest.util.ResourceUtils;
import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import com.bc.ceres.core.ProgressMonitor;

import java.io.File;


/**
 * Unit test for OceanTools Graph
 */
public class TestOceanToolsGraph extends TestCase {

    private static String graphFile = "OceanShipAndOilDetectionGraph.xml";
    private static String ASAR_IMM = "input\\ASA_IMM_1P_0739.N1";

    @Override
    protected void setUp() throws Exception {

    }

    @Override
    protected void tearDown() throws Exception {

    }

   public void testProcessGraph() throws GraphException {
        final File inputFile = new File(TestUtils.rootPathExpectedProducts, ASAR_IMM);
        final File outputFile = new File(ResourceUtils.getApplicationUserTempDataDir(), "tmpOut.dim");
        if(!inputFile.exists()) return;
        
     /*    final GraphExecuter graphEx = new GraphExecuter();
        graphEx.loadGraph(new File(ResourceUtils.getGraphFolder("User Graphs"), graphFile), false);

        GraphExecuter.setGraphIO(graphEx,
              "1-Read", inputFile.getAbsoluteFile(),
              "8-Write", outputFile.getAbsoluteFile(),
                DimapProductConstants.DIMAP_FORMAT_NAME);

        graphEx.InitGraph();
        graphEx.executeGraph(ProgressMonitor.NULL);   */
    }
}