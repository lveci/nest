package org.esa.nest.dat.plugins;

import org.esa.beam.framework.gpf.graph.NodeSource;

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
 * Edits the graph graphically
 */
public class GraphPanel extends JPanel implements ActionListener, PopupMenuListener, MouseListener, MouseMotionListener {

    private final GraphExecuter graphEx;
    final JMenu addMenu;
    private Point lastMousePos;
    private final int nodeWidth = 60;
    private final int nodeHeight = 30;
    private final int halfNodeHeight = nodeHeight/2;
    private final int hotSpotWidth = 10;
    private final int hotSpotOffset = halfNodeHeight - (hotSpotWidth/2);
    private final Color opColor = new Color(200, 200, 255, 128);
    private final Color selColor = new Color(200, 255, 200, 150);
    private GraphNode selectedNode;
    private boolean showSourceHotSpot = false;
    private boolean connectingSource = false;
    private Point connectingSourcePos;
    private GraphNode connectSourceTargetNode;

    GraphPanel(GraphExecuter graphExec) {

        graphEx = graphExec;
        Set gpfOperatorSet = graphEx.GetOperatorList();

        JMenuItem item;
        addMenu = new JMenu("Add");
        for (Object anAliasSet : gpfOperatorSet) {
            String alias = (String) anAliasSet;
            addMenu.add(item = new JMenuItem(alias, new ImageIcon("icons/Gears20.gif")));
            item.setHorizontalTextPosition(JMenuItem.RIGHT);
            item.addActionListener(this);
        }
        
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        DrawGraph(g, graphEx.GetGraphNodes());
    }

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
            g.drawString(n.getNode().getOperatorName(), x + 10, y + 20);

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


    public void mousePressed(MouseEvent e) {
        checkPopup(e);

        if(showSourceHotSpot) {
             connectingSource = true;
        }

        lastMousePos = e.getPoint();
    }

    public void mouseClicked(MouseEvent e) {
        checkPopup(e);
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
        checkPopup(e);

        if(connectingSource) {
            GraphNode n = findNode(e.getPoint());
            if(n != null && selectedNode != n) {
                graphEx.addOperatorSource(n, connectSourceTargetNode);
            }
        }
        connectingSource = false;
        connectSourceTargetNode = null;
        repaint();
    }

    public void mouseDragged(java.awt.event.MouseEvent e) {

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

    public void mouseMoved(java.awt.event.MouseEvent e) {
  
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

    public void actionPerformed(ActionEvent event) {

        String name = event.getActionCommand();
        if(name.equals("Remove")) {

            graphEx.removeOperator(selectedNode);
        } else {
            graphEx.addOperator(name);
        }

        repaint();
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

    private boolean isWithinRect(Point o, int width, int height, Point p) {
        return p.x > o.x && p.y > o.y && p.x < o.x+width && p.y < o.y+height;
    }

}
