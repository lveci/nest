package org.esa.nest.gpf;

import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.nest.util.DialogUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.Arrays;

/**
 * User interface for GCPSelectionOp
 */
public class MosaicOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private final JComboBox resamplingMethod = new JComboBox(new String[] {MosaicOp.NEAREST_NEIGHBOUR,
                                                                              MosaicOp.BILINEAR_INTERPOLATION,
                                                                              MosaicOp.CUBIC_CONVOLUTION});

    private final JLabel resamplingMethodLabel = new JLabel("Resampling Method:");
    private final JComboBox projectionName = new JComboBox();

    private final JTextField pixelSizeX = new JTextField("");
    private final JTextField pixelSizeY = new JTextField("");
    private final JTextField sceneWidth = new JTextField("");
    private final JTextField sceneHeight = new JTextField("");
    private final JCheckBox averageCheckBox = new JCheckBox("Average Overlap");
    private final JCheckBox normalizeByMeanCheckBox = new JCheckBox("Normalize By Mean");
    private boolean changedByUser = false;

    private boolean average = false;
    private boolean normalizeByMean = false;

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        final String[] projectionsValueSet = getProjectionsValueSet();
        Arrays.sort(projectionsValueSet);
        for(String name : projectionsValueSet) {
            projectionName.addItem(name);
        }
        projectionName.setSelectedItem(paramMap.get("projectionName"));

        averageCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    average = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });

        normalizeByMeanCheckBox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    normalizeByMean = (e.getStateChange() == ItemEvent.SELECTED);
                }
        });

        sceneWidth.addKeyListener(new KeyListener() {
             public void keyPressed(KeyEvent e) {
             }
             public void keyReleased(KeyEvent e) {
             }
             public void keyTyped(KeyEvent e) {
                changedByUser = true;
             }
        });

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        resamplingMethod.setSelectedItem(paramMap.get("resamplingMethod"));
        projectionName.setSelectedItem(paramMap.get("projectionName"));

        int width = (Integer)paramMap.get("sceneWidth");
        int height = (Integer)paramMap.get("sceneHeight");

        if(!changedByUser && sourceProducts != null) {
            try {
                MosaicOp.SceneProperties scnProp = new MosaicOp.SceneProperties();
                MosaicOp.computeImageGeoBoundary(sourceProducts, scnProp);

                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProducts[0]);
                final double rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
                final double azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);

                MosaicOp.getSceneDimensions(rangeSpacing, azimuthSpacing, scnProp);

                width = scnProp.sceneWidth;
                height = scnProp.sceneHeight;
                long dim = (long)width*(long)height;
                while(dim > Integer.MAX_VALUE) {
                    width -= 1000;
                    height -= 1000;
                    dim = (long)width*(long)height;
                }
            } catch(Exception e) {
                width = 0;
                height = 0;
            }
        }

        sceneWidth.setText(String.valueOf(width));
        sceneHeight.setText(String.valueOf(height));

        average = (Boolean)paramMap.get("average");
        averageCheckBox.getModel().setPressed(average);

        normalizeByMean = (Boolean)paramMap.get("normalizeByMean");
        normalizeByMeanCheckBox.getModel().setPressed(normalizeByMean);

    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);
        paramMap.put("resamplingMethod", resamplingMethod.getSelectedItem());
        paramMap.put("projectionName", projectionName.getSelectedItem());
        paramMap.put("sceneWidth", Integer.parseInt(sceneWidth.getText()));
        paramMap.put("sceneHeight", Integer.parseInt(sceneHeight.getText()));

        paramMap.put("average", average);
        paramMap.put("normalizeByMean", normalizeByMean);
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
        contentPane.add(resamplingMethodLabel, gbc);
        gbc.gridx = 1;
        contentPane.add(resamplingMethod, gbc);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Map Projection:", projectionName);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Scene Width (pixels)", sceneWidth);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Scene Height (pixels)", sceneHeight);

        gbc.gridy++;
        contentPane.add(averageCheckBox, gbc);
        gbc.gridy++;
        contentPane.add(normalizeByMeanCheckBox, gbc);

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