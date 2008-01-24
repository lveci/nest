package org.esa.nest.dat.plugins;

import org.esa.beam.framework.gpf.graph.Node;

import java.awt.*;
import java.util.Map;
import java.util.HashMap;

import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;

/**
 * Represents a node of the graph for the GraphBuilder
 * Stores, saves and loads the display position for the node
 * User: lveci
 * Date: Jan 17, 2008
 */
public class GraphNode {

    private final Node node;
    private final Map<String, Object> parameterMap = new HashMap<String, Object>();

    GraphNode(Node n) {
        node = n;
    }

    /**
     * Gets the display position of a node
     * @return Point The position of the node
     */
    Point getPos() {
        return new Point(node.getDisplayPosX(), node.getDisplayPosY());
    }

    /**
     * Sets the display position of a node and writes it to the xml
     * @param p The position of the node
     */
    void setPos(Point p) {
        node.setDisplayPosition(p.x, p.y);
    }

    Node getNode() {
        return node;
    }

    Map<String, Object> getParameterMap() {
        return parameterMap;
    }

}
