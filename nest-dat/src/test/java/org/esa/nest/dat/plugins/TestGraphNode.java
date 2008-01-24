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


}