package org.esa.nest.gpf;

import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * User interface for CreateElevationBandOp
 */
public class CreateElevationOpUI extends BaseOperatorUI {

    private static final ElevationModelDescriptor[] descriptors = ElevationModelRegistry.getInstance().getAllDescriptors();
    private static final String[] demValueSet = new String[descriptors.length];

    static {
        for (int i = 0; i < descriptors.length; i++) {
            demValueSet[i] = descriptors[i].getName();
        }
    }

    private final JComboBox demName = new JComboBox(demValueSet);

    private final JTextField elevationBandName = new JTextField("");
    private final JTextField externalDEM = new JTextField("");

    private final JComboBox resamplingMethod = new JComboBox(
            new String[] { CreateElevationOp.NEAREST_NEIGHBOUR, CreateElevationOp.BILINEAR, CreateElevationOp.CUBIC } );

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        demName.setSelectedItem(paramMap.get("demName"));
        elevationBandName.setText(String.valueOf(paramMap.get("elevationBandName")));
        externalDEM.setText(String.valueOf(paramMap.get("externalDEM")));
        resamplingMethod.setSelectedItem(paramMap.get("resamplingMethod"));
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        paramMap.put("demName", demName.getSelectedItem());
        paramMap.put("elevationBandName", elevationBandName.getText());
        paramMap.put("externalDEM", externalDEM.getText());
        paramMap.put("resamplingMethod", resamplingMethod.getSelectedItem());
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Digital Elevation Model:", demName);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "External DEM:", externalDEM);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Elevation Band Name:", elevationBandName);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Resampling Method:", resamplingMethod);
        gbc.gridy++;

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

}