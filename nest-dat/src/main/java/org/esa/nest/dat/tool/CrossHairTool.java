
package org.esa.nest.dat.tool;

import org.esa.beam.framework.ui.tool.impl.SelectTool;
import org.esa.nest.util.ResourceUtils;

import javax.swing.*;
import java.awt.*;

/**
 * A tool used to select items in a {@link org.esa.beam.framework.ui.product.ProductSceneView}.
 */
public class CrossHairTool extends SelectTool {
    public static final String SELECT_TOOL_PROPERTY_NAME = "crossHairTool";

    @Override
    public Cursor getCursor() {
        final Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        final String cursorName = "crossHairCursor";
        final ImageIcon icon = ResourceUtils.LoadIcon("org/esa/nest/icons/CrossHairTool.gif");

        //Dimension bestCursorSize = defaultToolkit.getBestCursorSize(icon.getIconWidth(), icon.getIconHeight());
        //Point hotSpot = new Point((8 * bestCursorSize.width) / icon.getIconWidth(),
        //                          (8 * bestCursorSize.height) / icon.getIconHeight());
        final Point hotSpot = new Point(icon.getIconWidth() /2, icon.getIconHeight() /2);

        return defaultToolkit.createCustomCursor(icon.getImage(), hotSpot, cursorName);
    }
}