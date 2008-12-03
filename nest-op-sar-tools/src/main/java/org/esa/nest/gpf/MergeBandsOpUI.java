package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;

import javax.swing.*;

import java.util.Map;
import java.util.ArrayList;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 12, 2008
 * Time: 1:52:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class MergeBandsOpUI extends BaseOperatorUI {

    private String productName = "";
    private final JTextField nameField = new JTextField("");
    private String[] name = { "" };
    private final JList bandList = new JList(name);

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        JComponent panel = createPanel();
        initParameters();

        return panel;
    }

    public void initParameters() {
        nameField.setText((String)paramMap.get("productName"));

        Object selectedValues[] = bandList.getSelectedValues();

        String[] names = getBandNames();
        bandList.removeAll();
        bandList.setListData(names);

        int size = bandList.getModel().getSize();
        ArrayList<Integer> indeces = new ArrayList<Integer>(size);

        for (Object selectedValue : selectedValues) {
            String selValue = (String) selectedValue;
            //System.out.println("initParams: selected=" + selValue);

            for (int j = 0; j < size; ++j) {
                String val = (String) bandList.getModel().getElementAt(j);
                if (val.equals(selValue)) {
                    indeces.add(j);
                    break;
                }
            }
        }
        int[] selIndex = new int[indeces.size()];
        for(int i=0; i < indeces.size(); ++i) {
            selIndex[i] = indeces.get(i);
        }
        bandList.setSelectedIndices(selIndex);
    }

    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    public void updateParameters() {
        paramMap.put("productName", nameField.getText());

        Object selectedValues[] = bandList.getSelectedValues();
        String bandNames[] = new String[selectedValues.length];
        for(int i=0; i<selectedValues.length; ++i) {
            bandNames[i] = (String)selectedValues[i];
            //System.out.println("updateParams: bandName="+ bandNames[i]);
        }

        paramMap.put("selectedBandNames", bandNames);
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