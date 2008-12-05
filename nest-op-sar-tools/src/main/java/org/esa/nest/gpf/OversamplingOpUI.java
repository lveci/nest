package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;

import javax.swing.*;

import java.util.Map;
import java.awt.*;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 12, 2008
 * Time: 1:52:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class OversamplingOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private final JComboBox outputImageBy = new JComboBox(new String[] { UndersamplingOp.IMAGE_SIZE,
                                                                         UndersamplingOp.RATIO,
                                                                         UndersamplingOp.PIXEL_SPACING } );

    private final JTextField targetImageHeight = new JTextField("");
    private final JTextField targetImageWidth = new JTextField("");
    private final JTextField widthRatio = new JTextField("");
    private final JTextField heightRatio = new JTextField("");
    private final JTextField rangeSpacing = new JTextField("");
    private final JTextField azimuthSpacing = new JTextField("");

    private final JLabel targetImageHeightLabel = new JLabel("Rows:");
    private final JLabel targetImageWidthLabel = new JLabel("Columns:");
    private final JLabel widthRatioLabel = new JLabel("Width Ratio:");
    private final JLabel heightRatioLabel = new JLabel("Height Ratio:");
    private final JLabel rangeSpacingLabel = new JLabel("Range Spacing (m):");
    private final JLabel azimuthSpacingLabel = new JLabel("Azimuth Spacing (m):");
    private final JLabel outputImageByLabel = new JLabel("Output Image By:     ");

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        return panel;
    }

    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        outputImageBy.setSelectedItem(paramMap.get("outputImageBy"));
        targetImageHeight.setText(String.valueOf(paramMap.get("targetImageHeight")));
        targetImageWidth.setText(String.valueOf(paramMap.get("targetImageWidth")));
        widthRatio.setText(String.valueOf(paramMap.get("widthRatio")));
        heightRatio.setText(String.valueOf(paramMap.get("heightRatio")));
        rangeSpacing.setText(String.valueOf(paramMap.get("rangeSpacing")));
        azimuthSpacing.setText(String.valueOf(paramMap.get("azimuthSpacing")));
    }

    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);

        paramMap.put("outputImageBy", outputImageBy.getSelectedItem());
        paramMap.put("targetImageHeight", Integer.parseInt(targetImageHeight.getText()));
        paramMap.put("targetImageWidth", Integer.parseInt(targetImageWidth.getText()));
        paramMap.put("widthRatio", Float.parseFloat(widthRatio.getText()));
        paramMap.put("heightRatio", Float.parseFloat(heightRatio.getText()));
        paramMap.put("rangeSpacing", Float.parseFloat(rangeSpacing.getText()));
        paramMap.put("azimuthSpacing", Float.parseFloat(azimuthSpacing.getText()));
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
        contentPane.add(outputImageByLabel, _gbc);
        _gbc.gridx = 1;
        contentPane.add(outputImageBy, _gbc);
        outputImageBy.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                updateOutputImageBy(true);
            }
        });

        int savedY = ++_gbc.gridy;
        OperatorUIUtils.addTextField(contentPane, _gbc, targetImageHeightLabel, targetImageHeight);
        _gbc.gridy++;
        OperatorUIUtils.addTextField(contentPane, _gbc, targetImageWidthLabel, targetImageWidth);

        _gbc.gridy = savedY;
        OperatorUIUtils.addTextField(contentPane, _gbc, widthRatioLabel, widthRatio);
        _gbc.gridy++;
        OperatorUIUtils.addTextField(contentPane, _gbc, heightRatioLabel, heightRatio);

        _gbc.gridy = savedY;
        OperatorUIUtils.addTextField(contentPane, _gbc, rangeSpacingLabel, rangeSpacing);
        _gbc.gridy++;
        OperatorUIUtils.addTextField(contentPane, _gbc, azimuthSpacingLabel, azimuthSpacing);

        updateOutputImageBy(true);

        return contentPane;
    }

    private void enableRowColumn(boolean flag) {
        OperatorUIUtils.enableComponents(targetImageWidthLabel, targetImageWidth, flag);
        OperatorUIUtils.enableComponents(targetImageHeightLabel, targetImageHeight, flag);
    }

    private void enableRatio(boolean flag) {
        OperatorUIUtils.enableComponents(widthRatioLabel, widthRatio, flag);
        OperatorUIUtils.enableComponents(heightRatioLabel, heightRatio, flag);
    }

    private void enablePixelSpacing(boolean flag) {
        OperatorUIUtils.enableComponents(rangeSpacingLabel, rangeSpacing, flag);
        OperatorUIUtils.enableComponents(azimuthSpacingLabel, azimuthSpacing, flag);
    }

    private void updateOutputImageBy(boolean show) {
        if(show) {
            outputImageBy.setVisible(true);
            outputImageByLabel.setVisible(true);

            String item = (String)outputImageBy.getSelectedItem();
            if(item.equals(UndersamplingOp.IMAGE_SIZE)) {
                enableRowColumn(true);
                enableRatio(false);
                enablePixelSpacing(false);
            } else if(item.equals(UndersamplingOp.RATIO)){
                enableRowColumn(false);
                enableRatio(true);
                enablePixelSpacing(false);
            } else {
                enableRowColumn(false);
                enableRatio(false);
                enablePixelSpacing(true);
            }
        } else {
            outputImageBy.setVisible(false);
            outputImageByLabel.setVisible(false);
            enableRowColumn(false);
            enableRatio(false);
            enablePixelSpacing(false);
        }
    }
}