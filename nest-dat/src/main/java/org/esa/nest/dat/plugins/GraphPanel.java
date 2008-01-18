package org.esa.nest.dat.plugins;

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

    private GraphExecuter graphEx;
    JMenu addMenu;
    private Point lastMousePos;
    private int nodeWidth = 60;
    private int nodeHeight = 30;
    private int hotSpotWidth = 10;
    private int hotSpotOffset = nodeHeight/2 - (hotSpotWidth/2);
    private Color opColor = new Color(200, 200, 255, 128);
    private Color selColor = new Color(200, 255, 200, 150);
    private GraphNode selectedNode;
    private boolean showSourceHotSpot = false;

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
        }

        if(showSourceHotSpot && selectedNode != null) {
            Point p = selectedNode.getPos();
            g.drawOval(p.x - hotSpotWidth/2, p.y + hotSpotOffset, hotSpotWidth, hotSpotWidth);
        }
    }


    public void mousePressed(MouseEvent e) {
        checkPopup(e);

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
    }

    public void mouseDragged(java.awt.event.MouseEvent e) {

        if(selectedNode != null) {
            Point p = new Point(e.getX() - (lastMousePos.x - selectedNode.getPos().x),
                                e.getY() - (lastMousePos.y - selectedNode.getPos().y));
            selectedNode.setPos(p);
            lastMousePos = e.getPoint();
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
