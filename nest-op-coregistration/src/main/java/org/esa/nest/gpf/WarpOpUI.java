package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

/**
 * User interface for WarpOp
 */
public class WarpOpUI extends BaseOperatorUI {

    private final JComboBox warpPolynomialOrder = new JComboBox(new String[] { "1","2","3" } );
    private final JComboBox interpolationMethod = new JComboBox(new String[] {
           Warp2Op.NEAREST_NEIGHBOR, Warp2Op.BILINEAR,
           Warp2Op.TRI, Warp2Op.CC4P, Warp2Op.CC6P, Warp2Op.TS6P, Warp2Op.TS8P, Warp2Op.TS16P} );

    private final JTextField rmsThreshold = new JTextField("");

    final JCheckBox openResidualsFileCheckBox = new JCheckBox("Show Residuals");
    boolean openResidualsFile;

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        openResidualsFileCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    openResidualsFile = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        rmsThreshold.setText(String.valueOf(paramMap.get("rmsThreshold")));
        warpPolynomialOrder.setSelectedItem(paramMap.get("warpPolynomialOrder"));

        if(sourceProducts != null && sourceProducts.length > 0) {
            final String os = System.getProperty("sun.desktop");
            final String arch = System.getProperty("sun.arch.data.model");

            final boolean isComplex = OperatorUtils.isComplex(sourceProducts[0]);
            if(!isComplex || (os.equals("windows") && arch.equals("32"))) {
                interpolationMethod.removeAllItems();
                interpolationMethod.addItem(Warp2Op.NEAREST_NEIGHBOR);
                interpolationMethod.addItem(Warp2Op.BILINEAR);
            }
        }

        interpolationMethod.setSelectedItem(paramMap.get("interpolationMethod"));
    }


    @Override
    public UIValidation validateParameters() {

        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {

        paramMap.put("rmsThreshold", Float.parseFloat(rmsThreshold.getText()));
        paramMap.put("warpPolynomialOrder", Integer.parseInt((String)warpPolynomialOrder.getSelectedItem()));
        paramMap.put("interpolationMethod", interpolationMethod.getSelectedItem());
        paramMap.put("openResidualsFile", openResidualsFile);
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "RMS Threshold:", rmsThreshold);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Warp Polynomial Order:", warpPolynomialOrder);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Interpolation Method:", interpolationMethod);
        gbc.gridy++;

        gbc.gridx = 0;
        gbc.gridy++;
        contentPane.add(openResidualsFileCheckBox, gbc);

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

}