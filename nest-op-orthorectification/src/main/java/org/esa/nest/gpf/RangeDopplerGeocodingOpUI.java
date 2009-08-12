package org.esa.nest.gpf;

import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.visat.VisatApp;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.util.ResourceUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.io.File;

/**
 * User interface for GCPSelectionOp
 */
public class RangeDopplerGeocodingOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();
    private final JComboBox demName = new JComboBox();
    private static final String externalDEMStr = "External DEM";

    private final JComboBox demResamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});
    private final JComboBox imgResamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});

    private final JTextField pixelSpacing = new JTextField("");
    private final JTextField externalDEMFile = new JTextField("");
    private final JTextField externalDEMNoDataValue = new JTextField("");
    private final JButton externalDEMBrowseButton = new JButton("...");
    private final JLabel externalDEMFileLabel = new JLabel("External DEM:");
    private final JLabel externalDEMNoDataValueLabel = new JLabel("DEM No Data Value:");

    private final JCheckBox saveDEMCheckBox = new JCheckBox("Save DEM as band");
    private final JCheckBox saveLocalIncidenceAngleCheckBox = new JCheckBox("Save local incidence angle as band");
    private final JCheckBox saveProjectedLocalIncidenceAngleCheckBox = new JCheckBox("Save projected local incidence angle as band");
    private final JCheckBox applyRadiometricCalibrationCheckBox = new JCheckBox("Apply Radiometric and Terrain Correction");

    private boolean saveDEM = false;
    private boolean saveLocalIncidenceAngle = false;
    private boolean saveProjectedLocalIncidenceAngle = false;
    private boolean applyRadiometricCalibration = false;
    private boolean changedByUser = false;

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        final ElevationModelRegistry elevationModelRegistry = ElevationModelRegistry.getInstance();

        final ElevationModelDescriptor[] demDesciptors = elevationModelRegistry.getAllDescriptors();
        for(ElevationModelDescriptor dem : demDesciptors) {
            demName.addItem(dem.getName());
        }
        demName.addItem(externalDEMStr);

        initializeOperatorUI(operatorName, parameterMap);

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

        externalDEMBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final File file = VisatApp.getApp().showFileOpenDialog("External DEM File", false, null);
                externalDEMFile.setText(file.getAbsolutePath());
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
        applyRadiometricCalibrationCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    applyRadiometricCalibration = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        demName.setSelectedItem(paramMap.get("demName"));
        demResamplingMethod.setSelectedItem(paramMap.get("demResamplingMethod"));
        imgResamplingMethod.setSelectedItem(paramMap.get("imgResamplingMethod"));

        double pix = (Double)paramMap.get("pixelSpacing");
        if(!changedByUser && sourceProducts != null) {
                // calculate pixel spacing
        }
        pixelSpacing.setText(String.valueOf(pix));

        final File extFile = (File)paramMap.get("externalDEMFile");
        if(extFile != null) {
            externalDEMFile.setText(extFile.getAbsolutePath());
            externalDEMNoDataValue.setText(String.valueOf(paramMap.get("externalDEMNoDataValue")));
        }

        saveDEM = (Boolean)paramMap.get("saveDEM");
        saveDEMCheckBox.getModel().setPressed(saveDEM);

        saveLocalIncidenceAngle = (Boolean)paramMap.get("saveLocalIncidenceAngle");
        saveLocalIncidenceAngleCheckBox.getModel().setPressed(saveLocalIncidenceAngle);

        saveProjectedLocalIncidenceAngle = (Boolean)paramMap.get("saveProjectedLocalIncidenceAngle");
        saveProjectedLocalIncidenceAngleCheckBox.getModel().setPressed(saveProjectedLocalIncidenceAngle);

        applyRadiometricCalibration = (Boolean)paramMap.get("applyRadiometricCalibration");
        applyRadiometricCalibrationCheckBox.getModel().setPressed(applyRadiometricCalibration);
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);

        paramMap.put("demName", demName.getSelectedItem());
        paramMap.put("demResamplingMethod", demResamplingMethod.getSelectedItem());
        paramMap.put("imgResamplingMethod", imgResamplingMethod.getSelectedItem());

        paramMap.put("pixelSpacing", Double.parseDouble(pixelSpacing.getText()));

        final String extFileStr = externalDEMFile.getText();
        if(!extFileStr.isEmpty()) {
            paramMap.put("externalDEMFile", new File(extFileStr));
            paramMap.put("externalDEMNoDataValue", Double.parseDouble(externalDEMNoDataValue.getText()));
        }

        paramMap.put("saveDEM", saveDEM);
        paramMap.put("saveLocalIncidenceAngle", saveLocalIncidenceAngle);
        paramMap.put("saveProjectedLocalIncidenceAngle", saveProjectedLocalIncidenceAngle);
        paramMap.put("applyRadiometricCalibration", applyRadiometricCalibration);
    }

    private JComponent createPanel() {

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

        contentPane.add(saveDEMCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(saveLocalIncidenceAngleCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(saveProjectedLocalIncidenceAngleCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(applyRadiometricCalibrationCheckBox, gbc);

        //DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

    private void enableExternalDEM(boolean flag) {
        DialogUtils.enableComponents(externalDEMFileLabel, externalDEMFile, flag);
        DialogUtils.enableComponents(externalDEMNoDataValueLabel, externalDEMNoDataValue, flag);
        externalDEMBrowseButton.setVisible(flag);
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
}