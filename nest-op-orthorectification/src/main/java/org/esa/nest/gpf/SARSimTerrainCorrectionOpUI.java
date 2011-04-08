/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.gpf;

import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

/**
 * User interface for SARSimTerrainCorrectionOp
 */
public class SARSimTerrainCorrectionOpUI extends RangeDopplerGeocodingOpUI {

    private final JTextField rmsThreshold = new JTextField("");
    private final JTextField warpPolynomialOrder = new JTextField("");
    private final JCheckBox openShiftsFileCheckBox = new JCheckBox("Show Range and Azimuth Shifts");
    private boolean openShiftsFile = false;

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {
        JComponent pane = super.CreateOpTab(operatorName, parameterMap, appContext);

        openShiftsFileCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    openShiftsFile = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });
                
        return pane;
    }

    @Override
    public void initParameters() {
        super.initParameters();

        float threshold = (Float)paramMap.get("rmsThreshold");
        rmsThreshold.setText(String.valueOf(threshold));

        int order = (Integer)paramMap.get("warpPolynomialOrder");
        warpPolynomialOrder.setText(String.valueOf(order));

        openShiftsFile = (Boolean)paramMap.get("openShiftsFile");
        openShiftsFileCheckBox.getModel().setPressed(openShiftsFile);
    }

    @Override
    public void updateParameters() {
        super.updateParameters();

        paramMap.put("rmsThreshold", Float.parseFloat(rmsThreshold.getText()));
        paramMap.put("warpPolynomialOrder", Integer.parseInt(warpPolynomialOrder.getText()));
        paramMap.put("openShiftsFile", openShiftsFile);
    }

    @Override
    protected JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "RMS Threshold:", rmsThreshold);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "WARP Polynomial Order:", warpPolynomialOrder);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Image Resampling Method:", imgResamplingMethod);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, sourcePixelSpacingsLabelPart1, sourcePixelSpacingsLabelPart2);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Pixel Spacing (m):", pixelSpacingInMeter);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Pixel Spacing (deg):", pixelSpacingInDegree);

        pixelSpacingInMeter.addFocusListener(new FocusListener() {

            public void focusGained(final FocusEvent e) {
            }
            public void focusLost(final FocusEvent e) {
                Double pixM, pixD;
                try {
                    pixM = Double.parseDouble(pixelSpacingInMeter.getText());
                    pixD = RangeDopplerGeocodingOp.getPixelSpacingInDegree(pixM);
                } catch (Exception ec) {
                    pixD = 0.0;
                }
                pixelSpacingInDegree.setText(String.valueOf(pixD));
            }
        });

        pixelSpacingInDegree.addFocusListener(new FocusListener() {

            public void focusGained(final FocusEvent e) {
            }
            public void focusLost(final FocusEvent e) {
                Double pixM, pixD;
                try {
                    pixD = Double.parseDouble(pixelSpacingInDegree.getText());
                    pixM = RangeDopplerGeocodingOp.getPixelSpacingInMeter(pixD);
                } catch (Exception ec) {
                    pixM = 0.0;
                }
                pixelSpacingInMeter.setText(String.valueOf(pixM));
            }
        });
        
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Map Projection:", projectionName);

        gbc.gridx = 0;
        gbc.gridy++;
        contentPane.add(saveDEMCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(saveLocalIncidenceAngleCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(saveProjectedLocalIncidenceAngleCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(saveSelectedSourceBandCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(applyRadiometricNormalizationCheckBox, gbc);
        gbc.gridy++;
        gbc.insets.left = 20;
        contentPane.add(saveSigmaNoughtCheckBox, gbc);
        gbc.gridx = 1;
        gbc.insets.left = 1;
        contentPane.add(incidenceAngleForSigma0, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets.left = 20;
        contentPane.add(saveGammaNoughtCheckBox, gbc);
        gbc.gridx = 1;
        gbc.insets.left = 1;
        contentPane.add(incidenceAngleForGamma0, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets.left = 20;
        contentPane.add(saveBetaNoughtCheckBox, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.insets.left = 20;
        DialogUtils.addComponent(contentPane, gbc, auxFileLabel, auxFile);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, externalAuxFileLabel, externalAuxFile);
        gbc.gridx = 2;
        contentPane.add(externalAuxFileBrowseButton, gbc);
        /*
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.insets.left = 20;
        contentPane.add(externalAuxFileLabel, gbc);
        gbc.gridx = 1;
        gbc.insets.left = 1;
        contentPane.add(externalAuxFile, gbc);
        DialogUtils.enableComponents(externalAuxFileLabel, externalAuxFile, true);
        gbc.gridx = 2;
        contentPane.add(externalAuxFileBrowseButton, gbc);
        */
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.insets.left = 1;
        contentPane.add(openShiftsFileCheckBox, gbc);
        //DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }
}