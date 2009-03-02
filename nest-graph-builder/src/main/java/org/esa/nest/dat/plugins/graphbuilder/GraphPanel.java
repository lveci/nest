package org.esa.nest.dat.plugins.graphbuilder;

import org.esa.beam.framework.gpf.graph.NodeSource;
import org.esa.nest.util.DatUtils;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

/**
 * Draws and Edits the graph graphically
 * User: lveci
 * Date: Jan 15, 2008
 */
class GraphPanel extends JPanel implements ActionListener, PopupMenuListener, MouseListener, MouseMotionListener {

    private final GraphExecuter graphEx;
    private JMenu addMenu;
    private Point lastMousePos = null;
    private final AddMenuListener addListener = new AddMenuListener(this);
    private final RemoveSourceMenuListener removeSourceListener = new RemoveSourceMenuListener(this);

    private static final Color opColor = new Color(200, 200, 255, 128);
    private static final Color selColor = new Color(200, 255, 200, 150);
    private static final Color helpColor = new Color(250, 255, 250, 150);
    private GraphNode selectedNode = null;
    private boolean showSourceHotSpot = false;
    private boolean connectingSource = false;
    private Point connectingSourcePos = null;
    private GraphNode connectSourceTargetNode = null;
    private boolean showRightClickHelp = false;

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
        final ImageIcon opIcon = DatUtils.LoadIcon("org/esa/nest/icons/cog_add.png");
        addMenu = new JMenu("Add");

        // get operator list from graph executor
        final Set<String> gpfOperatorSet = graphEx.GetOperatorList();
        final String[] aliasList = new String[gpfOperatorSet.size()];
        gpfOperatorSet.toArray(aliasList);
        Arrays.sort(aliasList);

        // add operators
        for (String anAlias : aliasList) {
            if(!graphEx.isOperatorInternal(anAlias)) {
                final JMenuItem item = new JMenuItem(anAlias, opIcon);
                item.setHorizontalTextPosition(JMenuItem.RIGHT);
                item.addActionListener(addListener);
                addMenu.add(item);
            }
        }
    }

    void AddOperatorAction(String name)
    {
        final GraphNode newGraphNode = graphEx.addOperator(name);
        newGraphNode.setPos(lastMousePos);
        repaint();
    }

    void RemoveSourceAction(String id)
    {
        if(selectedNode != null) {
            final GraphNode source = graphEx.findGraphNode(id);
            selectedNode.disconnectOperatorSources(source.getID());
            repaint();
        }
    }

    /**
     * Handles menu item pressed events
     * @param event the action event
     */
    public void actionPerformed(ActionEvent event) {

        final String name = event.getActionCommand();
        if(name.equals("Delete")) {

            graphEx.removeOperator(selectedNode);
            repaint();
        }
    }

    private void checkPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {

            final JPopupMenu popup = new JPopupMenu();
            popup.add(addMenu);

            if(selectedNode != null) {
                final JMenuItem item = new JMenuItem("Delete");
                popup.add(item);
                item.setHorizontalTextPosition(JMenuItem.RIGHT);
                item.addActionListener(this);

                final NodeSource[] sources = selectedNode.getNode().getSources();
                if(sources.length > 0) {
                    final JMenu removeSourcedMenu = new JMenu("Remove Source");
                    for (NodeSource ns : sources) {
                        final JMenuItem nsItem = new JMenuItem(ns.getSourceNodeId());
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
            showRightClickHelp = false;
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
     * @param nodeList the list of graphNodes
     */
    private void DrawGraph(Graphics g, ArrayList<GraphNode> nodeList) {

        final Font font = new Font("Ariel", 10, 10);
        g.setFont(font);
        if(showRightClickHelp) {
            drawHelp(g, helpColor);
        }

        for(GraphNode n : nodeList) {
            
            if(n == selectedNode)
                n.drawNode(g, selColor);
            else
                n.drawNode(g, opColor);
        }

        // first pass sets the Size in drawNode according to string length
        for(GraphNode n : nodeList) {
            // connect source nodes
            g.setColor(Color.red);
            final NodeSource[] nSources = n.getNode().getSources();
            for (NodeSource nSource : nSources) {
                final GraphNode srcNode = graphEx.findGraphNode(nSource.getSourceNodeId());
                if(srcNode != null)
                    n.drawConnectionLine(g, srcNode);
            }
        }

        if(showSourceHotSpot && selectedNode != null) {
            selectedNode.drawHotspot(g, Color.red);
        }
        if(connectingSource && connectSourceTargetNode != null) {
            final Point p1 = connectSourceTargetNode.getPos();
            final Point p2 = connectingSourcePos;
            if(p1 != null && p2 != null) {
                g.setColor(Color.red);
                g.drawLine(p1.x, p1.y + connectSourceTargetNode.getHalfNodeHeight(), p2.x, p2.y);
            }
        }
    }

    void showRightClickHelp(boolean flag) {
        showRightClickHelp = flag;
    }

    private static void drawHelp(final Graphics g, final Color col) {
        final int x = (int)(g.getClipBounds().getWidth()/2);
        final int y = (int)(g.getClipBounds().getHeight()/2);

        final FontMetrics metrics = g.getFontMetrics();
        final String name = "Right click here to add an operator";
        final Rectangle2D rect = metrics.getStringBounds(name, g);
        final int stringWidth = (int) rect.getWidth();

        g.setColor(Color.black);
        g.drawString(name, x - stringWidth / 2, y);
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
        showRightClickHelp = false;
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
            final GraphNode n = findNode(e.getPoint());
            if(n != null && selectedNode != n) {

              /*  Field[] declaredFields = graphEx.graphContext.getNodeContext(n.getNode()).getOperator().getClass().getDeclaredFields();
                for (Field declaredField : declaredFields) {
                    SourceProduct sourceProductAnnotation = declaredField.getAnnotation(SourceProduct.class);
                    if (sourceProductAnnotation != null) {
                        processSourceProductField(declaredField, sourceProductAnnotation);
                    }
                    SourceProducts sourceProductsAnnotation = declaredField.getAnnotation(SourceProducts.class);
                    if (sourceProductsAnnotation != null) {
                        processSourceProductsField(declaredField, sourceProductsAnnotation);
                    }
                }   */


                connectSourceTargetNode.connectOperatorSource(n.getID());
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
            final Point p = new Point(e.getX() - (lastMousePos.x - selectedNode.getPos().x),
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
  
        final GraphNode n = findNode(e.getPoint());
        if(selectedNode != n) {
            showSourceHotSpot = false;
            selectedNode = n;
            graphEx.setSelectedNode(selectedNode);

            repaint();
        }
        if(selectedNode != null) {
            Point sourcePoint = new Point(n.getPos().x, n.getPos().y + selectedNode.getHotSpotOffset());
            if(isWithinRect(sourcePoint, GraphNode.getHotSpotSize(), GraphNode.getHotSpotSize(), e.getPoint())) {
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

       for(GraphNode n : graphEx.GetGraphNodes()) {
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
