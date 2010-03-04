package org.esa.nest.gpf;

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
public class TestOrthorectifyGraph extends TestCase {

    private static String graphFile1 = "Orthorectify.xml";
    private static String graphFile2 = "RemoveAntPat_Orthorectify.xml";
    private static String graphFile3 = "RemoveAntPat_SARSim_GCPSelection.xml";
    private static String graphFile4 = "Multilook-Calibrate-Orthorectify.xml";
    private static String ASAR_IMM = "input\\ASA_IMM_1P_0739.N1";
    private static String ASAR_IMM_SUB = "input\\subset_0_of_ENVISAT-ASA_IMM_1P_0739.dim";
    private static String ERS_PRI = "input\\subset_0_of_ERS-1_SAR_PRI-ORBIT_32506_DATE__02-OCT-1997_14_53_43.dim";

    @Override
    protected void setUp() throws Exception {

    }

    @Override
    protected void tearDown() throws Exception {

    }

    public void testOrthorectifyGraph() throws GraphException {
        final File inputFile = new File(TestUtils.rootPathExpectedProducts, ASAR_IMM);
        final File outputFile = new File(ResourceUtils.getApplicationUserTempDataDir(), "tmpOut.dim");
        if(!inputFile.exists()) return;

    /*     final GraphExecuter graphEx = new GraphExecuter();
        graphEx.loadGraph(new File(ResourceUtils.getGraphFolder("User Graphs"), graphFile1), false);

        GraphExecuter.setGraphIO(graphEx,
              "1-Read", inputFile.getAbsoluteFile(),
              "2-Write", outputFile.getAbsoluteFile(),
                DimapProductConstants.DIMAP_FORMAT_NAME);

        graphEx.InitGraph();
        graphEx.executeGraph(ProgressMonitor.NULL);  */
    }
     /*
    public void testRemoveAntPat_OrthorectifyGraph() throws GraphException {
        final File inputFile = new File(TestUtils.rootPathExpectedProducts, ERS_PRI);
        final File outputFile = new File(ResourceUtils.getApplicationUserTempDataDir(), "tmpOut.dim");
        if(!inputFile.exists()) return;

        final GraphExecuter graphEx = new GraphExecuter();
        graphEx.loadGraph(new File(ResourceUtils.getGraphFolder("User Graphs"), graphFile2), false);

        GraphExecuter.setGraphIO(graphEx,
              "1-Read", inputFile.getAbsoluteFile(),
              "4-Write", outputFile.getAbsoluteFile(),
                DimapProductConstants.DIMAP_FORMAT_NAME);

        graphEx.InitGraph();
        graphEx.executeGraph(ProgressMonitor.NULL);
    }

    public void testRemoveAntPat_SARSim_GCPSelectionGraph() throws GraphException {
        final File inputFile = new File(TestUtils.rootPathExpectedProducts, ERS_PRI);
        final File outputFile = new File(ResourceUtils.getApplicationUserTempDataDir(), "tmpOut.dim");
        if(!inputFile.exists()) return;

        final GraphExecuter graphEx = new GraphExecuter();
        graphEx.loadGraph(new File(ResourceUtils.getGraphFolder("User Graphs"), graphFile3), false);

        GraphExecuter.setGraphIO(graphEx,
              "1-Read", inputFile.getAbsoluteFile(),
              "5-Write", outputFile.getAbsoluteFile(),
                DimapProductConstants.DIMAP_FORMAT_NAME);

        graphEx.InitGraph();
        graphEx.executeGraph(ProgressMonitor.NULL);
    }           */

  /*  public void testMultilookCalibrateOrthorectifyGraph() throws GraphException {
        final File inputFile = new File(TestUtils.rootPathExpectedProducts, ASAR_IMM);
        final File outputFile = new File(ResourceUtils.getApplicationUserTempDataDir(), "tmpOut.dim");
        if(!inputFile.exists()) return;

        final GraphExecuter graphEx = new GraphExecuter();
        graphEx.loadGraph(new File(ResourceUtils.getGraphFolder("User Graphs"), graphFile4), false);

        GraphExecuter.setGraphIO(graphEx,
              "1-Read", inputFile.getAbsoluteFile(),
              "4-Write", outputFile.getAbsoluteFile(),
                DimapProductConstants.DIMAP_FORMAT_NAME);

        graphEx.InitGraph();
        graphEx.executeGraph(ProgressMonitor.NULL);
    }   */
}