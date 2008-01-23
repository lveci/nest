package org.esa.nest.dat.plugins;

import junit.framework.TestCase;

import java.util.Vector;
import java.util.Set;

/**
 * GraphExecuter Tester.
 *
 * @author <Authors name>
 * @since <pre>12/21/2007</pre>
 * @version 1.0
 */
public class TestGraphExecuter extends TestCase {

    private GraphExecuter graphEx;

    public TestGraphExecuter(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        graphEx = new GraphExecuter();
    }

    public void tearDown() throws Exception {
        graphEx = null;
    }

    public void testGetOperators() {
        Set opList = graphEx.GetOperatorList();

        assertTrue(!opList.isEmpty());
    }

    public void testAddOperator() {

        graphEx.addOperator("testOp");

        Vector nodeList = graphEx.GetGraphNodes();
        assertEquals(1, nodeList.size());

        assertTrue(true);
    }
}
