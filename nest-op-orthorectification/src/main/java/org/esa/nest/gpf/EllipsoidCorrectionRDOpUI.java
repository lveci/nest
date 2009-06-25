package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * User interface for EllipsoidCorrectionRDOp
 */
public class EllipsoidCorrectionRDOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private final JComboBox imgResamplingMethod = new JComboBox(new String[] {EllipsoidCorrectionRDOp.NEAREST_NEIGHBOUR,
                                                                              EllipsoidCorrectionRDOp.BILINEAR,
                                                                              EllipsoidCorrectionRDOp.CUBIC});

    private final JLabel imgResamplingMethodLabel = new JLabel("Image resampling method:");

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());
        imgResamplingMethod.setSelectedItem(paramMap.get("imgResamplingMethod"));
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);
        paramMap.put("imgResamplingMethod", imgResamplingMethod.getSelectedItem());
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = GridBagUtils.createDefaultConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets.top = 1;
        gbc.insets.bottom = 1;
        gbc.insets.right = 1;
        gbc.insets.left = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPane.add(new JLabel("Source Bands:"), gbc);

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 1;
        contentPane.add(new JScrollPane(bandList), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy++;
        contentPane.add(imgResamplingMethodLabel, gbc);
        gbc.gridx = 1;
        contentPane.add(imgResamplingMethod, gbc);

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

}