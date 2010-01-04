package org.esa.nest.dat.dialogs;

import com.bc.ceres.swing.TableLayout;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;

import javax.swing.*;
import java.awt.*;

/**
 * NEST
 * User: lveci
 * Date: Jul 28, 2009
 */
class TargetFolderSelector extends TargetProductSelector {


    public JPanel createPanel() {

        final JPanel subPanel3 = new JPanel(new BorderLayout(3, 3));
        subPanel3.add(getProductDirLabel(), BorderLayout.NORTH);
        subPanel3.add(getProductDirTextField(), BorderLayout.CENTER);
        subPanel3.add(getProductDirChooserButton(), BorderLayout.EAST);

        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTableWeightX(1.0);

        tableLayout.setCellPadding(0, 0, new Insets(3, 3, 3, 3));
        tableLayout.setCellPadding(1, 0, new Insets(3, 3, 3, 3));
        tableLayout.setCellPadding(2, 0, new Insets(0, 24, 3, 3));
        tableLayout.setCellPadding(3, 0, new Insets(3, 3, 3, 3));

        final JPanel panel = new JPanel(tableLayout);
        panel.setBorder(BorderFactory.createTitledBorder("Target Folder"));
        panel.add(subPanel3);
        panel.add(getOpenInAppCheckBox());

        return panel;
    }
}
