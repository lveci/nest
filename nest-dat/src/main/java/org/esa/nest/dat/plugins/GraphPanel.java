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
    private final AddMenuListener addListener = new AddMenuListener(this);
    private final RemoveSourceMenuListener removeSourceListener = new RemoveSourceMenuListener(this);

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
            JMenuItem item = new JMenuItem((String)anAliasSet, opIcon);
            item.setHorizontalTextPosition(JMenuItem.RIGHT);
            item.addActionListener(addListener);
            addMenu.add(item);
        }
    }

    void AddOperatorAction(String name)
    {
        GraphNode newGraphNode = graphEx.addOperator(name);
        newGraphNode.setPos(lastMousePos);
        repaint();
    }

    void RemoveSourceAction(String id)
    {
        if(selectedNode != null) {
            GraphNode source = graphEx.findGraphNode(id);
            selectedNode.disconnectOperatorSources(source);
            repaint();
        }
    }

    /**
     * Handles menu item pressed events
     * @param event the action event
     */
    public void actionPerformed(ActionEvent event) {

        String name = event.getActionCommand();
        if(name.equals("Delete")) {

            graphEx.removeOperator(selectedNode);
            repaint();
        }
    }

    private void checkPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {

            JPopupMenu popup = new JPopupMenu();
            popup.add(addMenu);

            if(selectedNode != null) {
                JMenuItem item = new JMenuItem("Delete");
                popup.add(item);
                item.setHorizontalTextPosition(JMenuItem.RIGHT);
                item.addActionListener(this);

                NodeSource[] sources = selectedNode.getNode().getSources();
                if(sources.length > 0) {
                    JMenu removeSourcedMenu = new JMenu("Remove Source");
                    for (NodeSource ns : sources) {
                        JMenuItem nsItem = new JMenuItem(ns.getSourceNodeId());
                        removeSourcedMenu.add(nsItem);
                        nsItem.setHorizontalTextPosition(JMenuItem.RIGHT);
                        nsItem.addActionListener(removeSourceListener);
                    }
                    popup.add(removeSourcedMenu);
                }
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

        Font font = new Font("Ariel", 10, 10);
        g.setFont(font);
        for (Enumeration e = nodes.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();
            
            if(n == selectedNode)
                n.drawNode(g, selColor);
            else
                n.drawNode(g, opColor);

            g.setColor(Color.red);
            NodeSource[] nSources = n.getNode().getSources();
            for (NodeSource nSource : nSources) {
                GraphNode srcNode = graphEx.findGraphNode(nSource.getSourceNodeId());
                if(srcNode != null)
                    g.drawLine(n.getPos().x, n.getPos().y + n.getHalfNodeHeight(),
                            srcNode.getPos().x + srcNode.getWidth(), srcNode.getPos().y + srcNode.getHalfNodeHeight());
            }
        }

        if(showSourceHotSpot && selectedNode != null) {
            selectedNode.drawHotspot(g, Color.red);
        }
        if(connectingSource) {
            Point p1 = connectSourceTargetNode.getPos();
            Point p2 = connectingSourcePos;
            g.setColor(Color.red);
            g.drawLine(p1.x, p1.y + connectSourceTargetNode.getHalfNodeHeight(), p2.x, p2.y);
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
                connectSourceTargetNode.connectOperatorSource(n);
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
            Point sourcePoint = new Point(n.getPos().x, n.getPos().y + selectedNode.getHotSpotOffset());
            if(isWithinRect(sourcePoint, selectedNode.getHotSpotSize(), selectedNode.getHotSpotSize(), e.getPoint())) {
                 showSourceHotSpot = true;
                 connectSourceTargetNode = selectedNode;
                 repaint();
            } else if(showSourceHotSpot) {
                 showSourceHotSpot = false;
                 repaint();
            }
       }
    }

    private GraphNode findNode(Point p) {

       Vector nodes = graphEx.GetGraphNodes();
       for (Enumeration e = nodes.elements(); e.hasMoreElements();)
        {
            GraphNode n = (GraphNode) e.nextElement();

            if(isWithinRect(n.getPos(), n.getWidth(), n.getHeight(), p))
                return n;
        }
        return null;
    }

    private static boolean isWithinRect(Point o, int width, int height, Point p) {
        return p.x > o.x && p.y > o.y && p.x < o.x+width && p.y < o.y+height;
    }

    static class AddMenuListener implements ActionListener {

        final GraphPanel graphPanel;
        AddMenuListener(GraphPanel panel) {
            graphPanel = panel;
        }
        public void actionPerformed(java.awt.event.ActionEvent event) {
            graphPanel.AddOperatorAction(event.getActionCommand());
        }

    }

    static class RemoveSourceMenuListener implements ActionListener {

        final GraphPanel graphPanel;
        RemoveSourceMenuListener(GraphPanel panel) {
            graphPanel = panel;
        }
        public void actionPerformed(java.awt.event.ActionEvent event) {
            graphPanel.RemoveSourceAction(event.getActionCommand());
        }

    }
}
