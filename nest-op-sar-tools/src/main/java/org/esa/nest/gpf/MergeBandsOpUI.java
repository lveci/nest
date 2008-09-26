package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.DefaultTreeCellRenderer;

import java.util.Map;
import java.util.Enumeration;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 12, 2008
 * Time: 1:52:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class MergeBandsOpUI extends BaseOperatorUI {

    String productName = "";
    JTextField nameField = new JTextField("");
    String[] name = { "" };
    JList bandList = new JList(name);

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        JComponent panel = createPanel();
        initParameters();

        return panel;
    }

    public void initParameters() {
        nameField.setText((String)paramMap.get("productName"));

        String[] names = getBandNames();
        bandList.removeAll();
        bandList.setListData(names);
    }

    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    public void updateParameters() {
        paramMap.put("productName", nameField.getText());
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new FlowLayout(FlowLayout.LEFT));

        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints _gbc = GridBagUtils.createDefaultConstraints();
        _gbc.fill = GridBagConstraints.HORIZONTAL;
        _gbc.anchor = GridBagConstraints.NORTHWEST;
        _gbc.weighty = 1;
        _gbc.insets.top = 2;
        _gbc.insets.bottom = 2;

        _gbc.gridy++;
        _gbc.weightx = 0;
        contentPane.add(new JLabel("Product Name:"), _gbc);
        _gbc.weightx = 1;
        contentPane.add(nameField, _gbc);
        _gbc.gridy++;
        _gbc.weightx = 0;
        contentPane.add(new JLabel("Source Bands:"), _gbc);
        _gbc.fill = GridBagConstraints.BOTH;
        _gbc.weightx = 1;
        _gbc.weighty = 400;
        contentPane.add(bandList, _gbc);
        _gbc.fill = GridBagConstraints.HORIZONTAL;
        _gbc.weighty = 1;


        return contentPane;
    }

}