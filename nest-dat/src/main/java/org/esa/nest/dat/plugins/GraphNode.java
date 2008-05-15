package org.esa.nest.dat.plugins;

import com.thoughtworks.xstream.io.xml.xppdom.Xpp3Dom;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.graph.Node;
import org.esa.beam.framework.gpf.graph.NodeSource;
import org.esa.beam.framework.gpf.ui.OperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a node of the graph for the GraphBuilder
 * Stores, saves and loads the display position for the node
 * User: lveci
 * Date: Jan 17, 2008
 */
public class GraphNode {

    private final Node node;
    private final Map<String, Object> parameterMap = new HashMap<String, Object>();
    private final OperatorUI operatorUI;

    private int nodeWidth = 60;
    private int nodeHeight = 30;
    private int halfNodeHeight;
    private int halfNodeWidth;
    static final private int hotSpotSize = 10;
    private int hotSpotOffset;

    GraphNode(Node n) {
        node = n;
        operatorUI = CreateOperatorUI();
        initParameters();
    }

    void initParameters() {

        Xpp3Dom config = node.getConfiguration();
        int count = config.getChildCount();
        for (int i = 0; i < count; ++i) {
            Xpp3Dom child = config.getChild(i);

            parameterMap.put(child.getName(), child.getValue());
        }
    }

    void AssignParameters() {

        updateParameterMap();
        Xpp3Dom config = node.getConfiguration();
        Set keys = parameterMap.keySet();                           // The set of keys in the map.
        for (Object key : keys) {
            Object value = parameterMap.get(key);                   // Get the value for that key.
            if (value == null) continue;

            Xpp3Dom xml = config.getChild((String) key);
            if (xml == null) {
                xml = new Xpp3Dom((String) key);
                config.addChild(xml);
            }

            xml.setValue(value.toString());
        }
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

    static int getHotSpotSize() {
        return hotSpotSize;
    }

    int getHalfNodeWidth() {
        return halfNodeWidth;
    }

    int getHalfNodeHeight() {
        return halfNodeHeight;
    }

    private void setSize(int width, int height) {
        nodeWidth = width;
        nodeHeight = height;
        halfNodeHeight = nodeHeight / 2;
        halfNodeWidth = nodeWidth / 2;
        hotSpotOffset = halfNodeHeight - (hotSpotSize / 2);
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
            if (ns.getSourceNodeId().equals(source.getID())) {
                node.removeSource(ns);
            }
        }
    }

    UIValidation validateParameterMap() {
        return operatorUI.validateParameters();
    }

    void updateParameterMap() {
        if(operatorUI != null)
            operatorUI.updateParameters();
    }

    OperatorUI GetOperatorUI() {
        return operatorUI;
    }

    private OperatorUI CreateOperatorUI() {
        String operatorName = getOperatorName();
        final OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            return null;
        }

        return operatorSpi.createOperatorUI();
    }

    /**
     * Draw a GraphNode as a rectangle with a name
     * @param g The Java2D Graphics
     * @param col The color to draw
     */
    void drawNode(Graphics g, Color col) {
        int x = getPos().x;
        int y = getPos().y;

        FontMetrics metrics = g.getFontMetrics();
        String name = getOperatorName();
        Rectangle2D rect = metrics.getStringBounds(name, g);
        int stringWidth = (int) rect.getWidth();
        setSize(Math.max(stringWidth, 50) + 10, 30);

        g.setColor(col);
        g.fill3DRect(x, y, nodeWidth, nodeHeight, true);
        g.setColor(Color.blue);
        g.draw3DRect(x, y, nodeWidth, nodeHeight, true);

        g.setColor(Color.black);
        g.drawString(name, x + (nodeWidth - stringWidth) / 2, y + 20);
    }

    /**
     * Draws the hotspot where the user can join the node to a source node
     * @param g The Java2D Graphics
     * @param col The color to draw
     */
    void drawHotspot(Graphics g, Color col) {
        Point p = getPos();
        g.setColor(col);
        g.drawOval(p.x - hotSpotSize / 2, p.y + hotSpotOffset, hotSpotSize, hotSpotSize);
    }

    /**
     * Draw a line between source and target nodes
     * @param g The Java2D Graphics
     * @param src the source GraphNode
     */
    public void drawConnectionLine(Graphics g, GraphNode src) {

        Point tail = getPos();
        Point head = src.getPos();
        if (tail.x + nodeWidth < head.x) {
            drawArrow(g, tail.x + nodeWidth, tail.y + halfNodeHeight,
                    head.x, head.y + src.getHalfNodeHeight());
        } else if (tail.x < head.x + nodeWidth && head.y > tail.y) {
            drawArrow(g, tail.x + halfNodeWidth, tail.y + nodeHeight,
                    head.x + src.getHalfNodeWidth(), head.y);
        } else if (tail.x < head.x + nodeWidth && head.y < tail.y) {
            drawArrow(g, tail.x + halfNodeWidth, tail.y,
                    head.x + src.getHalfNodeWidth(), head.y + nodeHeight);
        } else {
            drawArrow(g, tail.x, tail.y + halfNodeHeight,
                    head.x + src.getWidth(), head.y + src.getHalfNodeHeight());
        }
    }

    /**
     * Draws an arrow head at the correct angle
     * @param g The Java2D Graphics
     * @param tailX position X on target node
     * @param tailY position Y on target node
     * @param headX position X on source node
     * @param headY position Y on source node
     */
    static private void drawArrow(Graphics g, int tailX, int tailY, int headX, int headY) {

        double t1 = Math.abs(headY - tailY);
        double t2 = Math.abs(headX - tailX);
        double theta = Math.atan(t1 / t2);
        if (headX > tailX) {
            if (headY > tailY)
                theta = Math.PI + theta;
            else
                theta = -(Math.PI + theta);
        } else if (headX < tailX && headY > tailY)
            theta = 2 * Math.PI - theta;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        Point p2 = new Point(-8, -3);
        Point p3 = new Point(-8, +3);

        int x = (int)Math.round((cosTheta * p2.x) - (sinTheta * p2.y));
        p2.y = (int)Math.round((sinTheta * p2.x) + (cosTheta * p2.y));
        p2.x = x;
        x = (int)Math.round((cosTheta * p3.x) - (sinTheta * p3.y));
        p3.y = (int)Math.round((sinTheta * p3.x) + (cosTheta * p3.y));
        p3.x = x;

        p2.translate(tailX, tailY);
        p3.translate(tailX, tailY);

        g.drawLine(tailX, tailY, headX, headY);
        g.drawLine(tailX, tailY, p2.x, p2.y);
        g.drawLine(p3.x, p3.y, tailX, tailY);
    }

}
