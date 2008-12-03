package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;

import javax.swing.*;

import java.util.Map;
import java.util.ArrayList;
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
public class UndersamplingOpUI extends BaseOperatorUI {

    private JList bandList = new JList();
    private final JComboBox method = new JComboBox(new String[] {       UndersamplingOp.SUB_SAMPLING,
                                                                        UndersamplingOp.KERNEL_FILTERING });

    private final JTextField subSamplingX = new JTextField("");
    private final JTextField subSamplingY = new JTextField("");

    private final JComboBox filterType = new JComboBox(new String[] {   UndersamplingOp.SUMMARY,
                                                                        UndersamplingOp.EDGE_DETECT,
                                                                        UndersamplingOp.EDGE_ENHANCEMENT,
                                                                        UndersamplingOp.LOSS_PASS,
                                                                        UndersamplingOp.HIGH_PASS,
                                                                        UndersamplingOp.HORIZONTAL,
                                                                        UndersamplingOp.VERTICAL,
                                                                        UndersamplingOp.USER_DEFINED } );

    private final JComboBox filterSize = new JComboBox(new String[] {   UndersamplingOp.FILTER_SIZE_3x3,
                                                                        UndersamplingOp.FILTER_SIZE_5x5,
                                                                        UndersamplingOp.FILTER_SIZE_7x7 } );

    private final JComboBox outputImageBy = new JComboBox(new String[] { UndersamplingOp.IMAGE_SIZE,
                                                                         UndersamplingOp.RATIO,
                                                                         UndersamplingOp.PIXEL_SPACING } );

    private final JTextField targetImageHeight = new JTextField("");
    private final JTextField targetImageWidth = new JTextField("");
    private final JTextField widthRatio = new JTextField("");
    private final JTextField heightRatio = new JTextField("");
    private final JTextField rangeSpacing = new JTextField("");
    private final JTextField azimuthSpacing = new JTextField("");

    private final JLabel subSamplingXLabel = new JLabel("Sub-Sampling in X:");
    private final JLabel subSamplingYLabel = new JLabel("Sub-Sampling in Y:");
    private final JLabel filterTypeLabel = new JLabel("Filter Type:");
    private final JLabel filterSizeLabel = new JLabel("Filter Size:");
    private final JLabel targetImageHeightLabel = new JLabel("Rows:");
    private final JLabel targetImageWidthLabel = new JLabel("Columns:");
    private final JLabel widthRatioLabel = new JLabel("Width Ratio:");
    private final JLabel heightRatioLabel = new JLabel("Height Ratio:");
    private final JLabel rangeSpacingLabel = new JLabel("Range Spacing (m):");
    private final JLabel azimuthSpacingLabel = new JLabel("Azimuth Spacing (m):");
    private final JLabel outputImageByLabel = new JLabel("Output Image By:");

    private final JLabel kernelFileLabel = new JLabel("Kernel File:");
    private final JTextField kernelFile = new JTextField("");

    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        return panel;
    }

    public void initParameters() {

        final Object selectedValues[] = bandList.getSelectedValues();

        final String[] names = getBandNames();
        bandList.removeAll();
        bandList.setListData(names);

        final int size = bandList.getModel().getSize();
        final ArrayList<Integer> indeces = new ArrayList<Integer>(size);

        for (Object selectedValue : selectedValues) {
            final String selValue = (String) selectedValue;
            //System.out.println("initParams: selected=" + selValue);

            for (int j = 0; j < size; ++j) {
                final String val = (String) bandList.getModel().getElementAt(j);
                if (val.equals(selValue)) {
                    indeces.add(j);
                    break;
                }
            }
        }
        final int[] selIndex = new int[indeces.size()];
        for(int i=0; i < indeces.size(); ++i) {
            selIndex[i] = indeces.get(i);
        }
        bandList.setSelectedIndices(selIndex);

        method.setSelectedItem(paramMap.get("method"));
        subSamplingX.setText(String.valueOf(paramMap.get("subSamplingX")));
        subSamplingY.setText(String.valueOf(paramMap.get("subSamplingY")));

        filterType.setSelectedItem(paramMap.get("filterType"));
        filterSize.setSelectedItem(paramMap.get("filterSize"));
        File kFile = (File)paramMap.get("kernelFile");
        if(kFile != null)
            kernelFile.setText(kFile.getAbsolutePath());

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

        final Object selectedValues[] = bandList.getSelectedValues();
        final String bandNames[] = new String[selectedValues.length];
        for(int i=0; i<selectedValues.length; ++i) {
            bandNames[i] = (String)selectedValues[i];
            //System.out.println("updateParams: bandName="+ bandNames[i]);
        }

        paramMap.put("sourceBandNames", bandNames);

        paramMap.put("method", method.getSelectedItem());
        paramMap.put("subSamplingX", Integer.parseInt(subSamplingX.getText()));
        paramMap.put("subSamplingY", Integer.parseInt(subSamplingY.getText()));

        paramMap.put("filterType", filterType.getSelectedItem());
        paramMap.put("filterSize", filterSize.getSelectedItem());
        paramMap.put("kernelFile", new File(kernelFile.getText()));

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
        contentPane.add(new JLabel("Under-Sampling Method:"), _gbc);
        _gbc.gridx = 1;
        contentPane.add(method, _gbc);
        method.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                String item = (String)method.getSelectedItem();
                if(item.equals(UndersamplingOp.SUB_SAMPLING)) {
                    enableKernelFiltering(false);
                    enableSubSampling(true);
                    updateOutputImageBy(false);
                } else {
                    enableKernelFiltering(true);
                    enableSubSampling(false);
                    updateOutputImageBy(true);
                }
            }
        });

        int savedY = ++_gbc.gridy;
        addTextField(contentPane, _gbc, subSamplingXLabel, subSamplingX);
        _gbc.gridy++;
        addTextField(contentPane, _gbc, subSamplingYLabel, subSamplingY);

        _gbc.gridy = savedY;
        _gbc.gridx = 0;
        contentPane.add(filterTypeLabel, _gbc);
        _gbc.gridx = 1;
        contentPane.add(filterType, _gbc);
        filterType.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                String item = (String)filterType.getSelectedItem();
                if(item.equals(UndersamplingOp.USER_DEFINED)) {
                    enableTextField(kernelFileLabel, kernelFile, true);
                    filterSize.setVisible(false);
                } else {
                    enableTextField(kernelFileLabel, kernelFile, false);
                    filterSize.setVisible(true);
                }
            }
        });

        savedY = ++_gbc.gridy;
        _gbc.gridx = 0;
        contentPane.add(filterSizeLabel, _gbc);
        _gbc.gridx = 1;
        contentPane.add(filterSize, _gbc);
        enableKernelFiltering(false);

        _gbc.gridy = savedY;
        addTextField(contentPane, _gbc, kernelFileLabel, kernelFile);
        enableTextField(kernelFileLabel, kernelFile, false);

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

        savedY = ++_gbc.gridy;
        addTextField(contentPane, _gbc, targetImageHeightLabel, targetImageHeight);
        _gbc.gridy++;
        addTextField(contentPane, _gbc, targetImageWidthLabel, targetImageWidth);
        
        _gbc.gridy = savedY;
        addTextField(contentPane, _gbc, widthRatioLabel, widthRatio);
        _gbc.gridy++;
        addTextField(contentPane, _gbc, heightRatioLabel, heightRatio);

        _gbc.gridy = savedY;
        addTextField(contentPane, _gbc, rangeSpacingLabel, rangeSpacing);
        _gbc.gridy++;
        addTextField(contentPane, _gbc, azimuthSpacingLabel, azimuthSpacing);

        updateOutputImageBy(false);

        return contentPane;
    }

    private void enableSubSampling(boolean flag) {
        enableTextField(subSamplingXLabel, subSamplingX, flag);
        enableTextField(subSamplingYLabel, subSamplingY, flag);
    }

    private void enableKernelFiltering(boolean flag) {
        enableTextField(filterTypeLabel, filterType, flag);
        enableTextField(filterSizeLabel, filterSize, flag);
    }

    private void enableRowColumn(boolean flag) {
        enableTextField(targetImageWidthLabel, targetImageWidth, flag);
        enableTextField(targetImageHeightLabel, targetImageHeight, flag);
    }

    private void enableRatio(boolean flag) {
        enableTextField(widthRatioLabel, widthRatio, flag);
        enableTextField(heightRatioLabel, heightRatio, flag);
    }

    private void enablePixelSpacing(boolean flag) {
        enableTextField(rangeSpacingLabel, rangeSpacing, flag);
        enableTextField(azimuthSpacingLabel, azimuthSpacing, flag);
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

    private void enableTextField(JComponent label, JComponent field, boolean flag) {
        label.setVisible(flag);
        field.setVisible(flag);
    }

    private void addTextField(JPanel contentPane, GridBagConstraints gbc, JLabel label, JComponent component) {
        gbc.gridx = 0;
        contentPane.add(label, gbc);
        gbc.gridx = 1;
        contentPane.add(component, gbc);
    }

}