package org.esa.nest.gpf.filtering;

import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.nest.gpf.OperatorUIUtils;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 12, 2008
 * Time: 1:52:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpeckleFilterOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private final JComboBox filter = new JComboBox(new String[] {   SpeckleFilterOp.MEAN_SPECKLE_FILTER,
                                                                        SpeckleFilterOp.MEDIAN_SPECKLE_FILTER,
                                                                        SpeckleFilterOp.FROST_SPECKLE_FILTER,
                                                                        SpeckleFilterOp.GAMMA_MAP_SPECKLE_FILTER,
                                                                        SpeckleFilterOp.LEE_SPECKLE_FILTER  } );

    private final JLabel dampingFactorLabel = new JLabel("Damping Factor:");

    private final JTextField filterSizeX = new JTextField("");
    private final JTextField filterSizeY = new JTextField("");
    private final JTextField dampingFactor = new JTextField("");

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        return panel;
    }

    @Override
    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        filter.setSelectedItem(paramMap.get("filter"));
        filterSizeX.setText(String.valueOf(paramMap.get("filterSizeX")));
        filterSizeY.setText(String.valueOf(paramMap.get("filterSizeY")));
        dampingFactor.setText(String.valueOf(paramMap.get("dampingFactor")));
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);        

        paramMap.put("filter", filter.getSelectedItem());
        paramMap.put("filterSizeX", Integer.parseInt(filterSizeX.getText()));
        paramMap.put("filterSizeY", Integer.parseInt(filterSizeY.getText()));
        paramMap.put("dampingFactor", Integer.parseInt(dampingFactor.getText()));
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints _gbc = GridBagUtils.createDefaultConstraints();
        _gbc.fill = GridBagConstraints.HORIZONTAL;
        _gbc.anchor = GridBagConstraints.NORTHWEST;
        _gbc.insets.top = 2;
        _gbc.insets.bottom = 2;

        _gbc.gridx = 0;
        _gbc.gridy = 0;
        contentPane.add(new JLabel("Source Bands:"), _gbc);
        _gbc.fill = GridBagConstraints.BOTH;
        _gbc.gridx = 1;
        contentPane.add(new JScrollPane(bandList), _gbc);
        _gbc.fill = GridBagConstraints.HORIZONTAL;
        _gbc.gridy++;
        _gbc.gridx = 0;
        contentPane.add(new JLabel("Filter:"), _gbc);
        _gbc.gridx = 1;
        contentPane.add(filter, _gbc);
        filter.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                String item = (String)filter.getSelectedItem();
                if(item.equals(SpeckleFilterOp.FROST_SPECKLE_FILTER)) {
                    DialogUtils.enableComponents(dampingFactorLabel, dampingFactor, true);
                } else {
                    DialogUtils.enableComponents(dampingFactorLabel, dampingFactor, false);
                }
            }
        });

        _gbc.gridy++;
        DialogUtils.addComponent(contentPane, _gbc, "Filter Size X:   ", filterSizeX);
        _gbc.gridy++;
        DialogUtils.addComponent(contentPane, _gbc, "Filter Size Y:   ", filterSizeY);

        _gbc.gridy++;
        DialogUtils.addComponent(contentPane, _gbc, dampingFactorLabel, dampingFactor);
        DialogUtils.enableComponents(dampingFactorLabel, dampingFactor, false);

        return contentPane;
    }

}