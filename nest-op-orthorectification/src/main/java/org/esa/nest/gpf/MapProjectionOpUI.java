package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.swing.*;

import java.util.Map;
import java.util.Arrays;
import java.awt.*;

/**
 * User interface for GCPSelectionOp
 */
public class MapProjectionOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();
    private final JComboBox projectionName = new JComboBox();


    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);

        final String[] projectionsValueSet = getProjectionsValueSet();
        Arrays.sort(projectionsValueSet);
        for(String name : projectionsValueSet) {
            projectionName.addItem(name);
        }

        final JComponent panel = createPanel();
        initParameters();

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        projectionName.setSelectedItem(paramMap.get("projectionName"));
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);

        paramMap.put("projectionName", projectionName.getSelectedItem());
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

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Map Projection:", projectionName);
        gbc.gridy++;

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }


    private static String[] getProjectionsValueSet() {
        final MapProjection[] projections = MapProjectionRegistry.getProjections();
        final String[] projectionsValueSet = new String[projections.length];
        for (int i = 0; i < projectionsValueSet.length; i++) {
            projectionsValueSet[i] = projections[i].getName();
        }
        return projectionsValueSet;
    }
}