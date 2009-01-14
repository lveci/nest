package org.esa.nest.dat.plugins.graphbuilder;

import junit.framework.TestCase;

import java.util.Vector;
import java.util.Set;
import java.util.Observer;

import org.esa.beam.framework.gpf.graph.GraphException;
import org.esa.nest.dat.plugins.graphbuilder.GraphExecuter;
import org.esa.nest.dat.plugins.graphbuilder.GraphNode;

/**
 * GraphExecuter Tester.
 *
 * @author lveci
 * @since 12/21/2007
 * @version 1.0
 */
public class TestGraphExecuter extends TestCase implements Observer {

    private GraphExecuter graphEx;
    private String updateValue = "";

    public TestGraphExecuter(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        graphEx = new GraphExecuter();
        graphEx.addObserver(this);
    }

    public void tearDown() throws Exception {
        graphEx = null;
    }

    public void testGetOperators() {
        Set opList = graphEx.GetOperatorList();

        assertTrue(!opList.isEmpty());
    }

    public void testAddOperator() {
        updateValue = "";
        graphEx.addOperator("testOp");

        Vector nodeList = graphEx.GetGraphNodes();
        assertEquals(1, nodeList.size());
        assertEquals(updateValue, "Add");
    }

    public void testClear() {
        graphEx.addOperator("testOp");

        Vector nodeList = graphEx.GetGraphNodes();
        assertEquals(1, nodeList.size());

        graphEx.ClearGraph();
        assertEquals(0, nodeList.size());
    }

    public void testRemoveOperator() {
        GraphNode node = graphEx.addOperator("testOp");

        Vector nodeList = graphEx.GetGraphNodes();
        assertEquals(1, nodeList.size());

        updateValue = "";
        graphEx.removeOperator(node);
        assertEquals(0, nodeList.size());
        assertEquals(updateValue, "Remove");
    }

    public void testFindGraphNode() {
        GraphNode lostNode = graphEx.addOperator("lostOp");

        GraphNode foundNode = graphEx.findGraphNode(lostNode.getID());
        assertTrue(foundNode.equals(lostNode));

        graphEx.ClearGraph();
    }

    public void testSetSelected() {
        GraphNode node = graphEx.addOperator("testOp");

        updateValue = "";
        graphEx.setSelectedNode(node);

        assertEquals(updateValue, "Selected");

        graphEx.ClearGraph();
    }

    public void testCreateGraph() throws GraphException {
        GraphNode nodeA = graphEx.addOperator("testOp");
        GraphNode nodeB = graphEx.addOperator("testOp");

        nodeB.connectOperatorSource(nodeA);

        //graphEx.writeGraph("D:\\data\\testGraph.xml");

        //graphEx.executeGraph(new NullProgressMonitor());
    }

    /**
     Implements the functionality of Observer participant of Observer Design Pattern to define a one-to-many
     dependency between a Subject object and any number of Observer objects so that when the
     Subject object changes state, all its Observer objects are notified and updated automatically.

     Defines an updating interface for objects that should be notified of changes in a subject.
     * @param subject The Observerable subject
     * @param data optional data
     */
    public void update(java.util.Observable subject, java.lang.Object data) {

        GraphExecuter.GraphEvent event = (GraphExecuter.GraphEvent)data;
        GraphNode node = (GraphNode)event.getData();
        String opID = node.getNode().getId();
        if(event.getEventType() == GraphExecuter.events.ADD_EVENT) {
            updateValue = "Add";
        } else if(event.getEventType() == GraphExecuter.events.REMOVE_EVENT) {
            updateValue = "Remove";
        } else if(event.getEventType() == GraphExecuter.events.SELECT_EVENT) {
            updateValue = "Selected";
        }
    }
}
