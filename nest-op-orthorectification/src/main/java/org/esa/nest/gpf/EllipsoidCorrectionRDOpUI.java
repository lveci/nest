package org.esa.nest.gpf;

import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * User interface for EllipsoidCorrectionRDOp
 */
public class EllipsoidCorrectionRDOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private final JComboBox imgResamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});

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
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();
        contentPane.add(new JLabel("Source Bands:"), gbc);

        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 1;
        contentPane.add(new JScrollPane(bandList), gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Image Resampling Method:", imgResamplingMethod);

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

}