/*
 * $Id: CrossHairTool.java,v 1.1 2008-07-23 19:48:00 lveci Exp $
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
package org.esa.nest.dat.tool;

import com.bc.layer.AbstractLayer;
import com.bc.layer.Layer;
import com.bc.layer.LayerModel;
import com.bc.swing.GraphicsPane;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.tool.AbstractTool;
import org.esa.beam.framework.ui.tool.ToolInputEvent;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.nest.util.DatUtils;

import javax.swing.*;
import java.awt.*;

/**
 * A tool used to select items in a {@link org.esa.beam.framework.ui.product.ProductSceneView}.
 */
public class CrossHairTool extends AbstractTool {
    public static final String SELECT_TOOL_PROPERTY_NAME = "crossHairTool";

    private static final Delegator DRAG = new Delegator() {
        public void execute(AbstractTool delegate, ToolInputEvent event) {
            delegate.mouseDragged(event);
        }
    };
    private static final Delegator MOVE = new Delegator() {
        public void execute(AbstractTool delegate, ToolInputEvent event) {
            delegate.mouseMoved(event);
        }
    };
    private static final Delegator RELEASE = new Delegator() {
        public void execute(AbstractTool delegate, ToolInputEvent event) {
            delegate.mouseReleased(event);
        }
    };
    private static final Delegator PRESS = new Delegator() {
        public void execute(AbstractTool delegate, ToolInputEvent event) {
            delegate.mousePressed(event);
        }
    };
    private static final Delegator CLICK = new Delegator() {
        public void execute(AbstractTool delegateTool, ToolInputEvent event) {
            delegateTool.mouseClicked(event);
        }
    };

    @Override
    public void mouseClicked(ToolInputEvent e) {
        handleInputEvent(e, CLICK);
    }

    @Override
    public void mousePressed(ToolInputEvent e) {
        handleInputEvent(e, PRESS);
    }

    @Override
    public void mouseReleased(ToolInputEvent e) {
        handleInputEvent(e, RELEASE);
    }

    @Override
    public void mouseMoved(ToolInputEvent e) {
        handleInputEvent(e, MOVE);
    }

    @Override
    public void mouseDragged(ToolInputEvent e) {
        handleInputEvent(e, DRAG);
    }

    private AbstractTool getDelegateTool(Layer layer) {
        AbstractTool delegate = null;
        if (layer instanceof AbstractLayer) {
            AbstractLayer abstractLayer = (AbstractLayer) layer;
            final Object value = abstractLayer.getPropertyValue(SELECT_TOOL_PROPERTY_NAME);
            if (value instanceof AbstractTool) {
                delegate = (AbstractTool) value;
            }
        }
        return delegate;
    }

    private void handleInputEvent(ToolInputEvent e, Delegator method) {
        final GraphicsPane graphicsPane = (GraphicsPane) e.getComponent();
        final LayerModel layerModel = graphicsPane.getLayerModel();
        for (int i = 0; i < layerModel.getLayerCount(); i++) {
            final Layer layer = layerModel.getLayer(i);
            AbstractTool delegate = getDelegateTool(layer);
            if (delegate != null) {
                method.execute(delegate, e);
            }
        }
    }

    private interface Delegator {
        public void execute(AbstractTool delegate, ToolInputEvent event);
    }

    public Cursor getCursor() {
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        final String cursorName = "crossHairCursor";
        ImageIcon icon = DatUtils.LoadIcon("org/esa/nest/icons/CrossHairTool.gif");

        Dimension bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
        //Point hotSpot = new Point((8 * bestCursorSize.width) / icon.getIconWidth(),
        //                          (8 * bestCursorSize.height) / icon.getIconHeight());
        Point hotSpot = new Point(icon.getIconWidth() /2, icon.getIconHeight() /2);

        return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
    }
}