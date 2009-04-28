/*
 * $Id: PopupMenuHandler.java,v 1.1 2009-04-28 14:17:18 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.esa.beam.framework.ui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPopupMenu;

import org.esa.beam.util.Guardian;

/**
 * A handler which can be registered on components as a mouse listener.
 * <p/>
 * <p>This handler pops-up a popup menu if the corresponding event is a popup menu trigger on a given platform. The
 * popup-menu is created by the <code>PopupMenuFactory</code> instance passed to the constructor of this class.
 *
 * @author Norman Fomferra
 * @version $Revision: 1.1 $  $Date: 2009-04-28 14:17:18 $
 * @see PopupMenuFactory
 */
public class PopupMenuHandler implements MouseListener, KeyListener {

    private PopupMenuFactory _popupMenuFactory;

    /**
     * Constructs a new pop-up menu handler for th given pop-up menu factory.
     *
     * @param popupMenuFactory the factory for the menu, must not be <code>null</code>
     */
    public PopupMenuHandler(PopupMenuFactory popupMenuFactory) {
        Guardian.assertNotNull("popupMenuFactory", popupMenuFactory);
        _popupMenuFactory = popupMenuFactory;
    }

    /**
     * Invoked when the mouse has been clicked on a component.
     */
    public void mouseClicked(MouseEvent event) {
        maybeShowPopupMenu(event);
    }

    /**
     * Invoked when a mouse button has been pressed on a component.
     */
    public void mousePressed(MouseEvent event) {
        maybeShowPopupMenu(event);
    }

    /**
     * Invoked when a mouse button has been released on a component.
     */
    public void mouseReleased(MouseEvent event) {
        maybeShowPopupMenu(event);
    }

    /**
     * Invoked when the mouse enters a component.
     */
    public void mouseEntered(MouseEvent event) {
    }

    /**
     * Invoked when the mouse exits a component.
     */
    public void mouseExited(MouseEvent event) {
    }

    /**
     * Invoked when a key has been pressed.
     */
    public void keyPressed(KeyEvent event) {
    }

    /**
     * Invoked when a key has been released.
     */
    public void keyReleased(KeyEvent event) {
        maybeShowPopupMenu(event);
    }

    /**
     * Invoked when a key has been typed. This event occurs when a key press is followed by a key release.
     */
    public void keyTyped(KeyEvent event) {
        maybeShowPopupMenu(event);
    }

    private void maybeShowPopupMenu(MouseEvent event) {
        if (event.isPopupTrigger()) {
            if (event.getComponent().isVisible()) {
                showPopupMenu(event);
            }
        }
    }

    private void maybeShowPopupMenu(KeyEvent event) {
        // @todo 1 nf/nf - proove event for pop-up triggerabilty here
    }

    private void showPopupMenu(MouseEvent event) {
        JPopupMenu popupMenu = _popupMenuFactory.createPopupMenu(event.getComponent());
        if (popupMenu == null) {
            popupMenu = _popupMenuFactory.createPopupMenu(event);
        }
        UIUtils.showPopup(popupMenu, event);
    }
}