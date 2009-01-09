package org.esa.nest.util;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.beans.PropertyChangeListener;

/**
 * NEST
 * User: lveci
 * Date: Jan 8, 2009
 */
public class DialogUtils {


    public static void enableComponents(JComponent label, JComponent field, boolean flag) {
        label.setVisible(flag);
        field.setVisible(flag);
    }

    public static void addTextField(JPanel contentPane, GridBagConstraints gbc, JLabel label, JComponent component) {
        gbc.gridx = 0;
        contentPane.add(label, gbc);
        gbc.gridx = 1;
        contentPane.add(component, gbc);
    }

    public static JFormattedTextField createFormattedTextField(final NumberFormat numFormat, final Object value,
                                                     final PropertyChangeListener propListener) {
        final JFormattedTextField field = new JFormattedTextField(numFormat);
        field.setValue(value);
        field.setColumns(10);
        if(propListener != null)
            field.addPropertyChangeListener("value", propListener);

        return field;
    }
}
