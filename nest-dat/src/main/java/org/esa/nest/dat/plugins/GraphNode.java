package org.esa.nest.dat.plugins;

import org.esa.beam.framework.gpf.graph.Node;

import java.awt.*;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a node of the graph for the GraphBuilder
 * User: lveci
 * Date: Jan 17, 2008
 */
public class GraphNode {

    private Node node;
    private Point pos = new Point(0, 0);
    private Map<String, Object> parameterMap = new HashMap<String, Object>();

    GraphNode(Node n) {
        node = n;
    }

    Point getPos() {
        return pos;
    }

    void setPos(Point p) {
        pos = p;
    }

    Node getNode() {
        return node;
    }

    Map<String, Object> getParameterMap() {
        return parameterMap;
    }
}
