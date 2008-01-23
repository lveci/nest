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
    private Point pos = new Point(0, 0);
    private final Map<String, Object> parameterMap = new HashMap<String, Object>();
    final String DISPLAY_POSITION = "DisplayPosition";
    final String X_POS = "xPos";
    final String Y_POS = "yPos";

    GraphNode(Node n) {
        node = n;

        readPositionFromXML();
    }

    /**
     * Gets the display position of a node
     * @return Point The position of the node
     */
    Point getPos() {
        return pos;
    }

    /**
     * Sets the display position of a node and writes it to the xml
     * @param p The position of the node
     */
    void setPos(Point p) {
        pos = p;

        writePositionToXML();
    }

    Node getNode() {
        return node;
    }

    Map<String, Object> getParameterMap() {
        return parameterMap;
    }

    /**
     * Writes the the display poisition to XML in the nodes configuration
     */
    private void writePositionToXML() {
        Xpp3Dom xml = node.getConfiguration().getChild(DISPLAY_POSITION);
        if(xml != null) {
            String value = "" + pos.x;
            xml.setAttribute(X_POS, value);
            value = "" + pos.y;
            xml.setAttribute(Y_POS, value);
        }
    }

    /**
     * Reads the the display poisition from XML in the nodes configuration
     * Creates a DisplayPosition element if it doesn't exist
     */
    private void readPositionFromXML() {
        // check if position element exists
        Xpp3Dom xml = node.getConfiguration().getChild(DISPLAY_POSITION);
        if(xml == null) {
            xml = new Xpp3Dom(DISPLAY_POSITION);
            xml.setAttribute(X_POS, "0");
            xml.setAttribute(Y_POS, "0");

            node.getConfiguration().addChild(xml);
        } else {
            pos.x = Integer.parseInt(xml.getAttribute(X_POS));
            pos.y = Integer.parseInt(xml.getAttribute(Y_POS));
        }
    }
}
