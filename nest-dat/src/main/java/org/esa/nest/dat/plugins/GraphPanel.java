package org.esa.nest.dat.plugins;

import org.esa.beam.framework.gpf.ui.DefaultSingleTargetProductDialog;
import org.esa.nest.dat.DatContext;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Set;

/**
 * User: lveci
 * Date: Jan 15, 2008
 * Time: 4:39:22 PM
 * Edits the graph graphically
 */
public class GraphPanel extends JPanel implements ActionListener, PopupMenuListener, MouseListener {

    GraphExecuter graphEx;
    JPopupMenu popup;
    DatContext appContext = new DatContext("GraphBuilder");

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
    }

    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.blue);
        g.draw3DRect(25, 10, 50, 75, true);
        g.draw3DRect(25, 110, 50, 75, false);
        g.fill3DRect(100, 10, 50, 75, true);
        g.fill3DRect(100, 110, 50, 75, false);


    }

    public void mousePressed(MouseEvent e) {
        checkPopup(e);
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
        try {
        System.out.println("Popup menu item [" + event.getActionCommand() +
                "] was pressed.");

        DefaultSingleTargetProductDialog dialog =
                new DefaultSingleTargetProductDialog(event.getActionCommand(), appContext, event.getActionCommand(), null);

        dialog.show();

        } catch(IllegalArgumentException e) {
        
        }
    }

}
