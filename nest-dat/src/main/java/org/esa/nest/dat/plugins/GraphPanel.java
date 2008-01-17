package org.esa.nest.dat.plugins;

import org.esa.nest.dat.DatContext;
import org.esa.beam.framework.gpf.graph.Node;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

/**
 * User: lveci
 * Date: Jan 15, 2008
 * Time: 4:39:22 PM
 * Edits the graph graphically
 */
public class GraphPanel extends JPanel implements ActionListener, PopupMenuListener, MouseListener, MouseMotionListener {

    private GraphExecuter graphEx;
    private JPopupMenu popup;
    private Node selectedNode;
    private Point lastMousePos;
    private int nodeWidth = 60;
    private int nodeHeight = 30;
    private Color opColor = new Color(200, 200, 255, 128);

    GraphPanel(GraphExecuter graphExec) {

        graphEx = graphExec;
        Set gpfOperatorSet = graphEx.GetOperatorList();

        popup = new JPopupMenu();
        JMenuItem item;

        JMenu addMenu = new JMenu("Add");
        for (Object anAliasSet : gpfOperatorSet) {
            String alias = (String) anAliasSet;
            addMenu.add(item = new JMenuItem(alias, new ImageIcon("icons/Gears20.gif")));
            item.setHorizontalTextPosition(JMenuItem.RIGHT);
            item.addActionListener(this);
        }
        popup.add(addMenu);

        popup.addSeparator();
        popup.add(item = new JMenuItem("Settings . . ."));
        item.addActionListener(this);
        popup.setLabel("Justification");
        popup.setBorder(new BevelBorder(BevelBorder.RAISED));
        popup.addPopupMenuListener(this);
        
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        DrawGraph(g, graphEx.GetNodes());
    }

    private void DrawGraph(Graphics g, Node[] nodes) {

        for(Node n : nodes) {
            
            int x = n.getDisplayXPosition();
            int y = n.getDisplayYPosition();
            g.setColor(opColor);
            g.fill3DRect(x, y, nodeWidth, nodeHeight, true);
            g.setColor(Color.blue);
            g.draw3DRect(x, y, nodeWidth, nodeHeight, true);

            g.setColor(Color.black);
            g.drawString(n.getOperatorName(), x + 5, y + 20);
        }
    }


    public void mousePressed(MouseEvent e) {
        checkPopup(e);

        lastMousePos = e.getPoint();
        selectedNode = findSelectedNode(lastMousePos);
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
            selectedNode.setDisplayXPosition(e.getX() - (lastMousePos.x - selectedNode.getDisplayXPosition()));
            selectedNode.setDisplayYPosition(e.getY() - (lastMousePos.y - selectedNode.getDisplayYPosition()));
            lastMousePos = e.getPoint();
            repaint();
        }
    }

    public void mouseMoved(java.awt.event.MouseEvent e) {

    }

    private void checkPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
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
 
        String opName = event.getActionCommand();
        System.out.println("Popup menu item [" + opName + "] was pressed.");

        graphEx.addOperator(opName);

        repaint();
    }

    private Node findSelectedNode(Point p) {

        Node[] nodes = graphEx.GetNodes();
        for(Node n : nodes) {

            if(isWithinRect(n.getDisplayXPosition(), n.getDisplayYPosition(), nodeWidth, nodeHeight, p.x, p.y))
                return n;
        }
        return null;
    }

    private boolean isWithinRect(int x, int y, int width, int height, int px, int py) {
        return px > x && py > y && px < x+width && py < y+height;
    }
}
