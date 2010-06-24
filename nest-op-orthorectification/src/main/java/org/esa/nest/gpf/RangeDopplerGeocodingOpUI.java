package org.esa.nest.gpf;

import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Arrays;
import java.util.Map;

/**
 * User interface for RangeDopplerGeocodingOp
 */
public class RangeDopplerGeocodingOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();
    private final JComboBox demName = new JComboBox();
    final JComboBox projectionName = new JComboBox();
    private static final String externalDEMStr = "External DEM";

    private final JComboBox demResamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});
    final JComboBox imgResamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});
    final JComboBox incidenceAngleForGamma0 = new JComboBox(new String[] {RangeDopplerGeocodingOp.USE_INCIDENCE_ANGLE_FROM_DEM,
                                                                           RangeDopplerGeocodingOp.USE_INCIDENCE_ANGLE_FROM_ELLIPSOID});
    final JComboBox incidenceAngleForSigma0 = new JComboBox(new String[] {RangeDopplerGeocodingOp.USE_INCIDENCE_ANGLE_FROM_DEM,
                                                                           RangeDopplerGeocodingOp.USE_INCIDENCE_ANGLE_FROM_ELLIPSOID});
    final JComboBox auxFile = new JComboBox(new String[] {CalibrationOp.LATEST_AUX,
                                                          CalibrationOp.PRODUCT_AUX,
                                                          CalibrationOp.EXTERNAL_AUX});

    final JTextField pixelSpacing = new JTextField("");
    private final JTextField externalDEMFile = new JTextField("");
    private final JTextField externalDEMNoDataValue = new JTextField("");
    private final JButton externalDEMBrowseButton = new JButton("...");
    private final JLabel externalDEMFileLabel = new JLabel("External DEM:");
    private final JLabel externalDEMNoDataValueLabel = new JLabel("DEM No Data Value:");

    final JCheckBox saveDEMCheckBox = new JCheckBox("Save DEM as a band");
    final JCheckBox saveLocalIncidenceAngleCheckBox = new JCheckBox("Save local incidence angle as a band");
    final JCheckBox saveProjectedLocalIncidenceAngleCheckBox = new JCheckBox("Save projected local incidence angle as a band");
    final JCheckBox saveSelectedSourceBandCheckBox = new JCheckBox("Save selected source band");
    final JCheckBox applyRadiometricNormalizationCheckBox = new JCheckBox("Apply radiometric normalization");
    final JCheckBox saveBetaNoughtCheckBox = new JCheckBox("Save Beta0 as a band");
    final JCheckBox saveGammaNoughtCheckBox = new JCheckBox("Save Gamma0 as a band");
    final JCheckBox saveSigmaNoughtCheckBox = new JCheckBox("Save Sigma0 as a band");

    final JLabel auxFileLabel = new JLabel("Auxiliary File:");
    final JLabel externalAuxFileLabel = new JLabel("External Aux File:");
    final JTextField externalAuxFile = new JTextField("");
    final JButton externalAuxFileBrowseButton = new JButton("...");

    private boolean saveDEM = false;
    private boolean saveLocalIncidenceAngle = false;
    private boolean saveProjectedLocalIncidenceAngle = false;
    private boolean saveSelectedSourceBand = false;
    private boolean applyRadiometricNormalization = false;
    private boolean saveBetaNought = false;
    private boolean saveGammaNought = false;
    private boolean saveSigmaNought = false;
    private boolean changedByUser = false;
    private double extNoDataValue = 0;

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();

        final ElevationModelDescriptor[] demDesciptors = elevationModelRegistry.getAllDescriptors();
        for(ElevationModelDescriptor dem : demDesciptors) {
            demName.addItem(dem.getName());
        }
        demName.addItem(externalDEMStr);

        initializeOperatorUI(operatorName, parameterMap);

        final String[] projectionsValueSet = getProjectionsValueSet();
        Arrays.sort(projectionsValueSet);
        for(String name : projectionsValueSet) {
            if (!name.contains("UTM Automatic")) {
                projectionName.addItem(name);
            }
        }

        final JComponent panel = createPanel();
        initParameters();

        demName.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                final String item = (String)demName.getSelectedItem();
                if(item.equals(externalDEMStr)) {
                    enableExternalDEM(true);
                } else {
                    externalDEMFile.setText("");
                    enableExternalDEM(false);
                }
            }
        });
        externalDEMFile.setColumns(30);
        demName.setSelectedItem(parameterMap.get("demName"));
        enableExternalDEM(false);

        auxFile.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                final String item = (String)auxFile.getSelectedItem();
                if(item.equals(CalibrationOp.EXTERNAL_AUX)) {
                    enableExternalAuxFile(true);
                } else {
                    externalAuxFile.setText("");
                    enableExternalAuxFile(false);
                }
            }
        });
        externalAuxFile.setColumns(30);
        auxFile.setSelectedItem(parameterMap.get("auxFile"));
        enableExternalAuxFile(false);

        externalDEMBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final File file = VisatApp.getApp().showFileOpenDialog("External DEM File", false, null);
                externalDEMFile.setText(file.getAbsolutePath());
                extNoDataValue = OperatorUIUtils.getNoDataValue(file);
                externalDEMNoDataValue.setText(String.valueOf(extNoDataValue));
            }
        });

        pixelSpacing.addKeyListener(new TextAreaKeyListener());

        saveDEMCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveDEM = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });
        saveLocalIncidenceAngleCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveLocalIncidenceAngle = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });
        saveProjectedLocalIncidenceAngleCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveProjectedLocalIncidenceAngle = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });
        saveSelectedSourceBandCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveSelectedSourceBand = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });

        applyRadiometricNormalizationCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {

                    applyRadiometricNormalization = (e.getStateChange() == ItemEvent.SELECTED);
                    if (applyRadiometricNormalization) {

                        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProducts[0]);
                        if (absRoot != null) {
                            final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION);

                            if (mission.equals("ENVISAT") || mission.contains("ERS") ||
                                mission.equals("RS2") || mission.equals("TSX1")) {

                                saveSigmaNoughtCheckBox.setEnabled(true);
                                saveSigmaNoughtCheckBox.getModel().setPressed(saveSigmaNought);
                                saveSigmaNoughtCheckBox.setSelected(true);
                                incidenceAngleForSigma0.setEnabled(true);
                                saveGammaNoughtCheckBox.setEnabled(true);
                                saveGammaNoughtCheckBox.getModel().setPressed(saveGammaNought);
                                incidenceAngleForGamma0.setEnabled(true);
                                saveBetaNoughtCheckBox.setEnabled(true);
                                saveBetaNoughtCheckBox.getModel().setPressed(saveBetaNought);
                                saveSelectedSourceBandCheckBox.setSelected(false);
                                auxFile.setEnabled(true);
                                auxFileLabel.setEnabled(true);
                                externalAuxFile.setEnabled(true);
                                externalAuxFileLabel.setEnabled(true);
                                externalAuxFileBrowseButton.setEnabled(true);

                            } else {

                                saveSigmaNoughtCheckBox.setSelected(false);
                                saveSigmaNoughtCheckBox.setEnabled(false);
                                saveGammaNoughtCheckBox.setEnabled(false);
                                saveBetaNoughtCheckBox.setEnabled(false);
                                incidenceAngleForSigma0.setEnabled(false);
                                incidenceAngleForGamma0.setEnabled(false);
                                saveSelectedSourceBandCheckBox.setSelected(true);
                                auxFile.setEnabled(false);
                                auxFileLabel.setEnabled(false);
                                externalAuxFile.setEnabled(false);
                                externalAuxFileLabel.setEnabled(false);
                                externalAuxFileBrowseButton.setEnabled(false);
                            }
                        }

                    } else {

                        saveSigmaNoughtCheckBox.setSelected(false);
                        saveSigmaNoughtCheckBox.setEnabled(false);
                        saveGammaNoughtCheckBox.setEnabled(false);
                        saveBetaNoughtCheckBox.setEnabled(false);
                        incidenceAngleForSigma0.setEnabled(false);
                        incidenceAngleForGamma0.setEnabled(false);
                        saveSelectedSourceBandCheckBox.setSelected(true);
                        auxFile.setEnabled(false);
                        auxFileLabel.setEnabled(false);
                        externalAuxFile.setEnabled(false);
                        externalAuxFileLabel.setEnabled(false);
                        externalAuxFileBrowseButton.setEnabled(false);
                    }
                }
        });
        saveBetaNoughtCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveBetaNought = (e.getStateChange() == ItemEvent.SELECTED);
                    if (saveBetaNought) {
                        saveSigmaNoughtCheckBox.setSelected(true);
                        saveProjectedLocalIncidenceAngleCheckBox.setSelected(true);
                    }
                }
        });
        saveGammaNoughtCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveGammaNought = (e.getStateChange() == ItemEvent.SELECTED);
                    if (saveGammaNought) {
                        saveSigmaNoughtCheckBox.setSelected(true);
                        saveProjectedLocalIncidenceAngleCheckBox.setSelected(true);
                    }
                }
        });
        saveSigmaNoughtCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    saveSigmaNought = (e.getStateChange() == ItemEvent.SELECTED);
                    if (saveSigmaNought) {
                        if (incidenceAngleForSigma0.getSelectedItem().equals(RangeDopplerGeocodingOp.USE_INCIDENCE_ANGLE_FROM_DEM)) {
                            saveProjectedLocalIncidenceAngleCheckBox.setSelected(false);
                        } else {
                            saveProjectedLocalIncidenceAngleCheckBox.setSelected(true);
                        }
                    }
                }
        });

        externalAuxFileBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final File file = VisatApp.getApp().showFileOpenDialog("External Aux File", false, null);
                externalAuxFile.setText(file.getAbsolutePath());
            }
        });

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {
        OperatorUIUtils.initBandList(bandList, getBandNames());

        demName.setSelectedItem(paramMap.get("demName"));
        projectionName.setSelectedItem(paramMap.get("projectionName"));
        demResamplingMethod.setSelectedItem(paramMap.get("demResamplingMethod"));
        imgResamplingMethod.setSelectedItem(paramMap.get("imgResamplingMethod"));
        incidenceAngleForGamma0.setSelectedItem(paramMap.get("incidenceAngleForGamma0"));
        incidenceAngleForSigma0.setSelectedItem(paramMap.get("incidenceAngleForSigma0"));

        if((!changedByUser || pixelSpacing.getText().isEmpty()) && sourceProducts != null) {
            Double pix;
            try {
                pix = RangeDopplerGeocodingOp.getPixelSpacing(sourceProducts[0]);
            } catch (Exception e) {
                pix = 0.0;
            }
            pixelSpacing.setText(String.valueOf(pix));
        }

        final File extDEMFile = (File)paramMap.get("externalDEMFile");
        if(extDEMFile != null) {
            externalDEMFile.setText(extDEMFile.getAbsolutePath());
            externalDEMNoDataValue.setText(String.valueOf(extNoDataValue));
        } else {
            externalDEMNoDataValue.setText(String.valueOf(paramMap.get("externalDEMNoDataValue")));
        }

        saveDEM = (Boolean)paramMap.get("saveDEM");
        saveDEMCheckBox.getModel().setPressed(saveDEM);

        saveLocalIncidenceAngle = (Boolean)paramMap.get("saveLocalIncidenceAngle");
        saveLocalIncidenceAngleCheckBox.getModel().setPressed(saveLocalIncidenceAngle);

        saveProjectedLocalIncidenceAngle = (Boolean)paramMap.get("saveProjectedLocalIncidenceAngle");
        saveProjectedLocalIncidenceAngleCheckBox.getModel().setPressed(saveProjectedLocalIncidenceAngle);

        saveSelectedSourceBand = (Boolean)paramMap.get("saveSelectedSourceBand");
        saveSelectedSourceBandCheckBox.getModel().setPressed(saveSelectedSourceBand);
        saveSelectedSourceBandCheckBox.setSelected(saveSelectedSourceBand);

        applyRadiometricNormalization = (Boolean)paramMap.get("applyRadiometricNormalization");
        applyRadiometricNormalizationCheckBox.getModel().setPressed(applyRadiometricNormalization);
        applyRadiometricNormalizationCheckBox.setSelected(applyRadiometricNormalization);

        saveBetaNought = (Boolean)paramMap.get("saveBetaNought");
        saveBetaNoughtCheckBox.getModel().setPressed(saveBetaNought);
        saveBetaNoughtCheckBox.setSelected(false);

        saveGammaNought = (Boolean)paramMap.get("saveGammaNought");
        saveGammaNoughtCheckBox.getModel().setPressed(saveGammaNought);
        saveGammaNoughtCheckBox.setSelected(false);

        saveSigmaNought = (Boolean)paramMap.get("saveSigmaNought");
        saveSigmaNoughtCheckBox.getModel().setPressed(saveSigmaNought);
        saveSigmaNoughtCheckBox.setSelected(saveSigmaNought);

        incidenceAngleForGamma0.setEnabled(applyRadiometricNormalization);
        incidenceAngleForSigma0.setEnabled(applyRadiometricNormalization);
        saveSigmaNoughtCheckBox.setEnabled(applyRadiometricNormalization);
        saveGammaNoughtCheckBox.setEnabled(applyRadiometricNormalization);
        saveBetaNoughtCheckBox.setEnabled(applyRadiometricNormalization);

        if(sourceProducts != null) {
            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProducts[0]);
            if (absRoot != null) {
                final String sampleType = absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
                if (sampleType.equals("COMPLEX")) {
                    auxFile.removeItem(CalibrationOp.PRODUCT_AUX);
                } else if (auxFile.getItemCount() == 2) {
                    auxFile.addItem(CalibrationOp.PRODUCT_AUX);
                }
            }
        }
        auxFile.setSelectedItem(paramMap.get("auxFile"));
        final File extAuxFile = (File)paramMap.get("externalAuxFile");
        if(extAuxFile != null) {
            externalAuxFile.setText(extAuxFile.getAbsolutePath());
        }
        auxFile.setEnabled(applyRadiometricNormalization);
        auxFileLabel.setEnabled(applyRadiometricNormalization);
        externalAuxFile.setEnabled(applyRadiometricNormalization);
        externalAuxFileLabel.setEnabled(applyRadiometricNormalization);
        externalAuxFileBrowseButton.setEnabled(applyRadiometricNormalization);
    }

    @Override
    public UIValidation validateParameters() {

        if (sourceProducts != null) {
            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProducts[0]);
            if(absRoot != null) {
                final boolean antElevCorrFlag = absRoot.getAttributeInt(AbstractMetadata.ant_elev_corr_flag) != 0;
                final boolean multilookFlag = absRoot.getAttributeInt(AbstractMetadata.multilook_flag) != 0;
                final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
                
                if ((mission.equals("ENVISAT") || mission.contains("ERS")) &&
                     applyRadiometricNormalization && antElevCorrFlag && multilookFlag) {
                    return new UIValidation(UIValidation.State.WARNING, "For multilooked products only" +
                            " constant and incidence angle corrections will be performed for radiometric normalization");
                }

                if (!mission.equals("RS2") && !mission.equals("TSX1") && !mission.equals("ENVISAT") &&
                    !mission.contains("ERS") && !mission.equals(" ") && applyRadiometricNormalization) {
                    applyRadiometricNormalization = false;
                    return new UIValidation(UIValidation.State.WARNING, "Radiometric normalization currently is" +
                            " not available for third party products except RadarSAT-2 and TerraSAR-X (SSC)");
                }
            }
        }
        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);

        paramMap.put("demName", demName.getSelectedItem());
        paramMap.put("projectionName", projectionName.getSelectedItem());
        paramMap.put("demResamplingMethod", demResamplingMethod.getSelectedItem());
        paramMap.put("imgResamplingMethod", imgResamplingMethod.getSelectedItem());
        paramMap.put("incidenceAngleForGamma0", incidenceAngleForGamma0.getSelectedItem());
        paramMap.put("incidenceAngleForSigma0", incidenceAngleForSigma0.getSelectedItem());
        if(pixelSpacing.getText().isEmpty()) {
            paramMap.put("pixelSpacing", 0.0);    
        } else {
            paramMap.put("pixelSpacing", Double.parseDouble(pixelSpacing.getText()));
        }

        final String extFileStr = externalDEMFile.getText();
        if(!extFileStr.isEmpty()) {
            paramMap.put("externalDEMFile", new File(extFileStr));
            paramMap.put("externalDEMNoDataValue", Double.parseDouble(externalDEMNoDataValue.getText()));
        }

        paramMap.put("saveDEM", saveDEM);
        paramMap.put("saveLocalIncidenceAngle", saveLocalIncidenceAngle);
        paramMap.put("saveProjectedLocalIncidenceAngle", saveProjectedLocalIncidenceAngle);
        paramMap.put("saveSelectedSourceBand", saveSelectedSourceBand);
        paramMap.put("applyRadiometricNormalization", applyRadiometricNormalization);
        paramMap.put("saveBetaNought", saveBetaNought);
        paramMap.put("saveGammaNought", saveGammaNought);
        paramMap.put("saveSigmaNought", saveSigmaNought);

        paramMap.put("auxFile", auxFile.getSelectedItem());
        final String extAuxFileStr = externalAuxFile.getText();
        if(!extAuxFileStr.isEmpty()) {
            paramMap.put("externalAuxFile", new File(extAuxFileStr));
        }
    }

    JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        contentPane.add(new JLabel("Source Bands:"), gbc);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 1;
        contentPane.add(new JScrollPane(bandList), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Digital Elevation Model:", demName);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, externalDEMFileLabel, externalDEMFile);
        gbc.gridx = 2;
        contentPane.add(externalDEMBrowseButton, gbc);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, externalDEMNoDataValueLabel, externalDEMNoDataValue);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "DEM Resampling Method:", demResamplingMethod);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Image Resampling Method:", imgResamplingMethod);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Pixel Spacing (m):", pixelSpacing);
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

        return contentPane;
    }

    private void enableExternalDEM(boolean flag) {
        DialogUtils.enableComponents(externalDEMFileLabel, externalDEMFile, flag);
        DialogUtils.enableComponents(externalDEMNoDataValueLabel, externalDEMNoDataValue, flag);
        externalDEMBrowseButton.setVisible(flag);
    }

    private static String[] getProjectionsValueSet() {
        final MapProjection[] projections = MapProjectionRegistry.getProjections();
        final String[] projectionsValueSet = new String[projections.length];
        for (int i = 0; i < projectionsValueSet.length; i++) {
            projectionsValueSet[i] = projections[i].getName();
        }
        return projectionsValueSet;
    }

    private class TextAreaKeyListener implements KeyListener {
        public void keyPressed(KeyEvent e) {
        }
        public void keyReleased(KeyEvent e) {
        }
        public void keyTyped(KeyEvent e) {
            changedByUser = true;
        }
    }

    private void enableExternalAuxFile(boolean flag) {
        DialogUtils.enableComponents(externalAuxFileLabel, externalAuxFile, flag);
        externalAuxFileBrowseButton.setVisible(flag);
    }
}