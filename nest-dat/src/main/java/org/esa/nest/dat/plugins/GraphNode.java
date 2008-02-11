package org.esa.nest.dat.plugins;

import org.esa.beam.framework.gpf.graph.Node;
import org.esa.beam.framework.gpf.graph.NodeSource;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.HashMap;

/**
 * Represents a node of the graph for the GraphBuilder
 * Stores, saves and loads the display position for the node
 * User: lveci
 * Date: Jan 17, 2008
 */
public class GraphNode {

    private final Node node;
    private final Map<String, Object> parameterMap = new HashMap<String, Object>();

    private int nodeWidth = 60;
    private int nodeHeight = 30;
    private int halfNodeHeight;
    private int hotSpotSize = 10;
    private int hotSpotOffset;

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

    int getWidth() {
        return nodeWidth;
    }

    int getHeight() {
        return nodeHeight;
    }

    int getHotSpotSize() {
        return hotSpotSize;
    }

    int getHalfNodeHeight() {
        return halfNodeHeight;
    }

    private void setSize(int width, int height) {
        nodeWidth = width;
        nodeHeight = height;
        halfNodeHeight = nodeHeight / 2;
        hotSpotOffset = nodeHeight/2 - (hotSpotSize/2);
    }

    int getHotSpotOffset() {
        return hotSpotOffset;
    }

    /**
     * Gets the uniqe node identifier.
     * @return the identifier
     */
    String getID() {
        return node.getId();
    }

     /**
     * Gets the name of the operator. 
     * @return the name of the operator.
     */
    String getOperatorName() {
        return node.getOperatorName();
    }

    Map<String, Object> getParameterMap() {
        return parameterMap;
    }

    void connectOperatorSource(GraphNode source) {
        // check if already a source for this node
        disconnectOperatorSources(source);
        
        NodeSource ns = new NodeSource("sourceProduct", source.getID());
        node.addSource(ns);
    }

    void disconnectOperatorSources(GraphNode source) {

        NodeSource[] sources = node.getSources();
        for (NodeSource ns : sources) {
            if(ns.getSourceNodeId().equals(source.getID())) {
                node.removeSource(ns);
            }
        }
    }

    void drawNode(Graphics g, Color col) {
        int x = getPos().x;
        int y = getPos().y;

        FontMetrics metrics = g.getFontMetrics();
        String name = getOperatorName();
        Rectangle2D rect = metrics.getStringBounds(name, g);
        int stringWidth = (int)rect.getWidth();
        setSize(Math.max(stringWidth, 50) + 10, 30);

        g.setColor(col);
        g.fill3DRect(x, y, nodeWidth, nodeHeight, true);
        g.setColor(Color.blue);
        g.draw3DRect(x, y, nodeWidth, nodeHeight, true);

        g.setColor(Color.black);
        g.drawString(name, x + (nodeWidth - stringWidth)/2, y + 20);
    }
    
    void drawHotspot(Graphics g, Color col) {
        Point p = getPos();
        g.setColor(col);
        g.drawOval(p.x - hotSpotSize/2, p.y + hotSpotOffset, hotSpotSize, hotSpotSize);
    }
      
}
