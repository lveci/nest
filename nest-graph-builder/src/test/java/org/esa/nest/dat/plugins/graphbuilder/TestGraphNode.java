package org.esa.nest.dat.plugins.graphbuilder;

import com.bc.ceres.binding.dom.Xpp3DomElement;
import junit.framework.TestCase;
import org.esa.beam.framework.gpf.graph.Node;
import org.esa.beam.framework.gpf.graph.NodeSource;

import java.awt.*;

/**
 * GraphNode Tester.
 *
 * @author lveci
 * @since <pre>12/21/2007</pre>
 * @version 1.0
 */
public class TestGraphNode extends TestCase {

    private Node node;
    private GraphNode graphNode;

    public TestGraphNode(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        node = new Node("id", "readOp");
        final Xpp3DomElement parameters = new Xpp3DomElement("parameters");
        node.setConfiguration(parameters);

        graphNode = new GraphNode(node);
    }

    @Override
    public void tearDown() throws Exception {
        node = null;
        graphNode = null;
    }

    public void testPosition() {
        Point p1 = new Point(1,2);
        graphNode.setPos(p1);  

        Point p2 = graphNode.getPos();

        assertEquals(p1, p2);
    }

    public void testNode() {

        assertEquals(node, graphNode.getNode());
        assertEquals(node.getId(), graphNode.getID());
        assertEquals(node.getOperatorName(), graphNode.getOperatorName());
    }

    public void testSourceConnection() {
        final Node sourceNode = new Node("sourceID", "testSourceNodeOp");
        final Xpp3DomElement parameters = new Xpp3DomElement("parameters");
        sourceNode.setConfiguration(parameters);

        GraphNode sourceGraphNode = new GraphNode(sourceNode);

        // test connect
        graphNode.connectOperatorSource(sourceGraphNode.getID());

        NodeSource ns = node.getSource(0);
        assertNotNull(ns);

        assertEquals(ns.getSourceNodeId(), sourceNode.getId());

        // test disconnect
        graphNode.disconnectOperatorSources(sourceGraphNode.getID());

        NodeSource [] nsList = node.getSources();
        assertEquals(nsList.length, 0);
    }
    
}