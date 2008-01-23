package org.esa.nest.dat.plugins;

import junit.framework.TestCase;
import org.esa.beam.framework.gpf.graph.Node;
import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;

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

    public TestGraphNode(String name) {
        super(name);
    }

    public void setUp() throws Exception {
        node = new Node("id", "testNodeOp");
        Xpp3Dom parameters = new Xpp3Dom("parameters");
        node.setConfiguration(parameters);
    }

    public void tearDown() throws Exception {
        node = null;
    }

    public void testInitialization() {

        assertNotNull(new GraphNode(node));
    }

    public void testSetPosition() {

        final int x = 20;
        final int y = 30;
        GraphNode gNode = new GraphNode(node);

        Point p = new Point(x, y);
        gNode.setPos(p);

        Xpp3Dom xml = node.getConfiguration().getChild(gNode.DISPLAY_POSITION);
        assertNotNull(xml);

        assertEquals(x, Integer.parseInt(xml.getAttribute(gNode.X_POS)));
        assertEquals(y, Integer.parseInt(xml.getAttribute(gNode.Y_POS)));
    }
}