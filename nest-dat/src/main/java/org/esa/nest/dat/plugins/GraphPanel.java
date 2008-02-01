package org.esa.nest.dat.plugins;

import org.esa.beam.framework.gpf.graph.NodeSource;
import org.esa.nest.util.DatUtils;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;
import java.util.Vector;
import java.util.Enumeration;

/**
 * User: lveci
 * Date: Jan 15, 2008
 * Draws and Edits the graph graphically
 */
public class GraphPanel extends JPanel implements ActionListener, PopupMenuListener, MouseListener, MouseMotionListener {

    private final GraphExecuter graphEx;
    private JMenu addMenu;
    private Point lastMousePos;
    private static final int nodeWidth = 60;
    private static final int nodeHeight = 30;
    private static final int halfNodeHeight = nodeHeight/2;
    private static final int hotSpotWidth = 10;
    private static final int hotSpotOffset = halfNodeHeight - (hotSpotWidth/2);
    private static final Color opColor = new Color(200, 200, 255, 128);
    private static final Color selColor = new Color(200, 255, 200, 150);
    private GraphNode selectedNode;
    private boolean showSourceHotSpot = false;
    private boolean connectingSource = false;
    private Point connectingSourcePos;
    private GraphNode connectSourceTargetNode;

    GraphPanel(GraphExecuter graphExec) {

        graphEx = graphExec;

        CreateAddOpMenu();
        
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    /**
     * Creates a menu containing the list of operators to the addMenu
     */
    private void CreateAddOpMenu() {
        ImageIcon opIcon = DatUtils.LoadIcon("org/esa/nest/icons/cog_add.png");
        addMenu = new JMenu("Add");

        // get operator list from graph executor
        Set gpfOperatorSet = graphEx.GetOperatorList();
        // add operators
        for (Object anAliasSet : gpfOperatorSet) {
            addMenuItem(addMenu, (String)anAliasSet, opIcon);
        }
    }

    private void addMenuItem(JMenu menu, String name, ImageIcon icon) {
        JMenuItem item = new JMenuItem(name, icon);
        item.setHorizontalTextPosition(JMenuItem.RIGHT);
        item.addActionListener(this);
        menu.add(item);
    }

    /**
     * Handles menu item pressed events
     * @param event the action event
     */
    public void actionPerformed(ActionEvent event) {

        String name = event.getActionCommand();
        if(name.equals("Remove")) {

            graphEx.removeOperator(selectedNode);
        } else {
            GraphNode newGraphNode = graphEx.addOperator(name);
            newGraphNode.setPos(lastMousePos);
        }

        repaint();
    }
    
    /**
     * Paints the panel component
     * @param g The Graphics
     */
    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        DrawGraph(g, graphEx.GetGraphNodes());
    }

    /**
     * Draw the graphical representation of the Graph
     * @param g the Graphics
     * @param nodes the list of graphNodes
     */
    private void DrawGraph(Graphics g, Vector nodes) {

        for (Enumeration e = nodes.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            
            int x = n.getPos().x;
            int y = n.getPos().y;
            if(n == selectedNode)
                g.setColor(selColor);
            else
                g.setColor(opColor);
            g.fill3DRect(x, y, nodeWidth, nodeHeight, true);
            g.setColor(Color.blue);
            g.draw3DRect(x, y, nodeWidth, nodeHeight, true);

            g.setColor(Color.black);
            g.drawString(n.getOperatorName(), x + 10, y + 20);

            g.setColor(Color.red);
            NodeSource[] nSources = n.getNode().getSources();
            for (NodeSource nSource : nSources) {
                GraphNode srcNode = graphEx.findGraphNode(nSource.getSourceNodeId());
                if(srcNode != null)
                    g.drawLine(n.getPos().x, n.getPos().y + halfNodeHeight,
                            srcNode.getPos().x + nodeWidth, srcNode.getPos().y + halfNodeHeight);
            }
        }

        if(showSourceHotSpot && selectedNode != null) {
            Point p = selectedNode.getPos();
            g.drawOval(p.x - hotSpotWidth/2, p.y + hotSpotOffset, hotSpotWidth, hotSpotWidth);
        }
        if(connectingSource) {
            Point p1 = connectSourceTargetNode.getPos();
            Point p2 = connectingSourcePos;
            g.setColor(Color.red);
            g.drawLine(p1.x, p1.y + halfNodeHeight, p2.x, p2.y);
        }
    }

    /**
     * Handle mouse pressed event
     * @param e the mouse event
     */
    public void mousePressed(MouseEvent e) {
        checkPopup(e);

        if(showSourceHotSpot) {
             connectingSource = true;
        }

        lastMousePos = e.getPoint();
    }

    /**
     * Handle mouse clicked event
     * @param e the mouse event
     */
    public void mouseClicked(MouseEvent e) {
        checkPopup(e);
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    /**
     * Handle mouse released event
     * @param e the mouse event
     */
    public void mouseReleased(MouseEvent e) {
        checkPopup(e);

        if(connectingSource) {
            GraphNode n = findNode(e.getPoint());
            if(n != null && selectedNode != n) {
                graphEx.connectOperatorSource(n, connectSourceTargetNode);
            }
        }
        connectingSource = false;
        connectSourceTargetNode = null;
        repaint();
    }

    /**
     * Handle mouse dragged event
     * @param e the mouse event
     */
    public void mouseDragged(MouseEvent e) {

        if(selectedNode != null && !connectingSource) {
            Point p = new Point(e.getX() - (lastMousePos.x - selectedNode.getPos().x),
                                e.getY() - (lastMousePos.y - selectedNode.getPos().y));
            selectedNode.setPos(p);
            lastMousePos = e.getPoint();
            repaint();
        }
        if(connectingSource) {
            connectingSourcePos = e.getPoint();
            repaint();
        }
    }

    /**
     * Handle mouse moved event
     * @param e the mouse event
     */
    public void mouseMoved(MouseEvent e) {
  
        GraphNode n = findNode(e.getPoint());
        if(selectedNode != n) {
            showSourceHotSpot = false;
            selectedNode = n;
            graphEx.setSelectedNode(selectedNode);

            repaint();
        }
        if(selectedNode != null) {
            Point sourcePoint = new Point(n.getPos().x, n.getPos().y + hotSpotOffset);
            if(isWithinRect(sourcePoint, hotSpotWidth, hotSpotWidth, e.getPoint())) {
                 showSourceHotSpot = true;
                 connectSourceTargetNode = selectedNode;
                 repaint();
            } else if(showSourceHotSpot) {
                 showSourceHotSpot = false;
                 repaint();
            }
       }
    }

    private void checkPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {

            JPopupMenu popup = new JPopupMenu();
            popup.add(addMenu);

            if(selectedNode != null) {
                JMenuItem item = new JMenuItem("Remove");
                popup.add(item);
                item.setHorizontalTextPosition(JMenuItem.RIGHT);
                item.addActionListener(this);
            }

            popup.setLabel("Justification");
            popup.setBorder(new BevelBorder(BevelBorder.RAISED));
            popup.addPopupMenuListener(this);
            popup.show(this, e.getX(), e.getY());
        }
    }

    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
    }

    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
    }

    public void popupMenuCanceled(PopupMenuEvent e) {
    }

    private GraphNode findNode(Point p) {

       Vector nodes = graphEx.GetGraphNodes();
       for (Enumeration e = nodes.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();

            if(isWithinRect(n.getPos(), nodeWidth, nodeHeight, p))
                return n;
        }
        return null;
    }

    private static boolean isWithinRect(Point o, int width, int height, Point p) {
        return p.x > o.x && p.y > o.y && p.x < o.x+width && p.y < o.y+height;
    }

}
