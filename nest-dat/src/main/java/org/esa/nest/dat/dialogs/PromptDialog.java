package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jun 5, 2008
 * To change this template use File | Settings | File Templates.
 */
public class PromptDialog extends ModalDialog {

    private JTextField prompt1;
    private boolean ok = false;

    public PromptDialog(String title, String label, String defaultValue) {
        super(VisatApp.getApp().getMainFrame(), title, ModalDialog.ID_OK_CANCEL, null);

        final JPanel content = GridBagUtils.createPanel();
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets.right = 4;
        gbc.gridy = 0;
        gbc.weightx = 0;

        gbc.insets.top = 2;
        prompt1 = addField(content, gbc, label, defaultValue);

        setContent(content);
    }

    private static JTextField addField(final JPanel content, final GridBagConstraints gbc,
                                 final String text, final String value) {
        content.add(new JLabel(text), gbc);
        gbc.weightx = 1;
        JTextField field = createTextField(value);
        content.add(field, gbc);
        gbc.gridy++;
        return field;
    }

    private static JTextField createTextField(final String value) {
        JTextField field = new JTextField(value);
        field.setEditable(true);
        field.setHorizontalAlignment(JTextField.RIGHT);
        return field;
    }

    public String getValue() {
        return prompt1.getText();
    }

    protected void onOK() {
        ok = true;
        hide();
    }

    public boolean IsOK() {
        return ok;
    }

}
