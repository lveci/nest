package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;

/**
 * User interface for GCPSelectionOp
 */
public class MosaicOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();

    private final JComboBox resamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});

    //private final JComboBox projectionName = new JComboBox();

    private final JTextField pixelSize = new JTextField("");
    private final JTextField sceneWidth = new JTextField("");
    private final JTextField sceneHeight = new JTextField("");
    private final JCheckBox averageCheckBox = new JCheckBox("Average Overlap");
    private final JCheckBox normalizeByMeanCheckBox = new JCheckBox("Normalize By Mean");

    private boolean changedByUser = false;
    private boolean average = false;
    private boolean normalizeByMean = false;

    private double widthHeightRatio = 1;
    private double pixelSizeHeightRatio = 1;
    private final MosaicOp.SceneProperties scnProp = new MosaicOp.SceneProperties();

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        //final String[] projectionsValueSet = getProjectionsValueSet();
        //Arrays.sort(projectionsValueSet);
        //for(String name : projectionsValueSet) {
        //    projectionName.addItem(name);
        //}

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        //projectionName.setSelectedItem(IdentityTransformDescriptor.NAME);
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

        pixelSize.addKeyListener(new TextAreaKeyListener());
        sceneWidth.addKeyListener(new TextAreaKeyListener());
        sceneHeight.addKeyListener(new TextAreaKeyListener());

        return new JScrollPane(panel);
    }

    @Override
    public void initParameters() {

        OperatorUIUtils.initBandList(bandList, getBandNames());

        resamplingMethod.setSelectedItem(paramMap.get("resamplingMethod"));
        //projectionName.setSelectedItem(paramMap.get("projectionName"));

        double pixSize = (Double)paramMap.get("pixelSize");
        int width = (Integer)paramMap.get("sceneWidth");
        int height = (Integer)paramMap.get("sceneHeight");

        if(!changedByUser && sourceProducts != null) {
            try {
                MosaicOp.computeImageGeoBoundary(sourceProducts, scnProp);

                final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProducts[0]);
                final double rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
                final double azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
                final double minSpacing = Math.min(rangeSpacing, azimuthSpacing);
                pixSize = minSpacing;
                
                MosaicOp.getSceneDimensions(minSpacing, scnProp);

                width = scnProp.sceneWidth;
                height = scnProp.sceneHeight;
                widthHeightRatio = width / (double)height;
                pixelSizeHeightRatio = pixSize / (double) height;

                long dim = (long)width*(long)height;
                while(dim > Integer.MAX_VALUE) {
                    width -= 1000;
                    height = (int)(width / widthHeightRatio);
                    dim = (long)width*(long)height;
                }
            } catch(Exception e) {
                width = 0;
                height = 0;
            }
        }

        pixelSize.setText(String.valueOf(pixSize));
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
        //paramMap.put("projectionName", projectionName.getSelectedItem());
        paramMap.put("pixelSize", Double.parseDouble(pixelSize.getText()));
        paramMap.put("sceneWidth", Integer.parseInt(sceneWidth.getText()));
        paramMap.put("sceneHeight", Integer.parseInt(sceneHeight.getText()));

        paramMap.put("average", average);
        paramMap.put("normalizeByMean", normalizeByMean);
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
        DialogUtils.addComponent(contentPane, gbc, "Resampling Method:", resamplingMethod);
        //gbc.gridy++;
        //DialogUtils.addComponent(contentPane, gbc, "Map Projection:", projectionName);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Pixel Size (m):", pixelSize);
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

    private class TextAreaKeyListener implements KeyListener {
        public void keyPressed(KeyEvent e) {
        }
        public void keyReleased(KeyEvent e) {
            try {
                changedByUser = true;
                if(e.getComponent() == pixelSize) {
                    final double pixSize = Double.parseDouble(pixelSize.getText());
                    MosaicOp.getSceneDimensions(pixSize, scnProp);

                    sceneWidth.setText(String.valueOf(scnProp.sceneWidth));
                    sceneHeight.setText(String.valueOf(scnProp.sceneHeight));
                } else if(e.getComponent() == sceneWidth) {
                    final int height = (int)(Integer.parseInt(sceneWidth.getText()) / widthHeightRatio);
                    sceneHeight.setText(String.valueOf(height));
                    pixelSize.setText(String.valueOf(height * pixelSizeHeightRatio));
                } else if(e.getComponent() == sceneHeight) {
                    final int width = (int)(Integer.parseInt(sceneHeight.getText()) / widthHeightRatio);
                    sceneWidth.setText(String.valueOf(width));
                    pixelSize.setText(String.valueOf(width * pixelSizeHeightRatio));
                }
            } catch(Exception ex) {
                //
            }
        }
        public void keyTyped(KeyEvent e) {
        }
    }
}