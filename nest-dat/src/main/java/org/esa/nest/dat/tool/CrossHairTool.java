
package org.esa.nest.dat.tool;

import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.tool.AbstractTool;
import org.esa.beam.framework.ui.tool.ToolInputEvent;
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

    private void handleInputEvent(ToolInputEvent e, Delegator method) {
        final ProductSceneView psv = (ProductSceneView)getDrawingEditor();
        final AbstractTool[] tools = psv.getSelectToolDelegates();
        for (AbstractTool tool : tools) {
            method.execute(tool, e);
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