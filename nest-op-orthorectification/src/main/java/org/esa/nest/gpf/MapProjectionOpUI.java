package org.esa.nest.gpf;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.dataop.maptransf.MapInfo;
import org.esa.beam.framework.dataop.maptransf.MapProjection;
import org.esa.beam.framework.dataop.maptransf.MapProjectionRegistry;
import org.esa.beam.framework.dataop.resamp.ResamplingFactory;
import org.esa.beam.framework.gpf.ui.BaseOperatorUI;
import org.esa.beam.framework.gpf.ui.UIValidation;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.util.DialogUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

/**
 * User interface for GCPSelectionOp
 */
public class MapProjectionOpUI extends BaseOperatorUI {

    private final JList bandList = new JList();
    private final JComboBox projectionName = new JComboBox();

    private final JComboBox resamplingMethod = new JComboBox(new String[] {ResamplingFactory.NEAREST_NEIGHBOUR_NAME,
                                                                           ResamplingFactory.BILINEAR_INTERPOLATION_NAME,
                                                                           ResamplingFactory.CUBIC_CONVOLUTION_NAME});

    private final JTextField pixelSizeX = new JTextField("");
    private final JTextField pixelSizeY = new JTextField("");
    private final JTextField easting = new JTextField("");
    private final JTextField northing = new JTextField("");
    private final JTextField orientation = new JTextField("");

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
        resamplingMethod.setSelectedItem(paramMap.get("resamplingMethod"));

        MapInfo outputMapInfo = null;
        if(sourceProducts != null) {
            final Product srcProduct = sourceProducts[0];
            final String quicklookBandName = ProductUtils.findSuitableQuicklookBandName(srcProduct);

            outputMapInfo = ProductUtils.createSuitableMapInfo(srcProduct,
                    MapProjectionRegistry.getProjection((String)projectionName.getSelectedItem()),
                    (Float)paramMap.get("orientation"),
                    srcProduct.getBand(quicklookBandName).getNoDataValue());
        }

        float pixSizeX = (Float)(paramMap.get("pixelSizeX"));
        if(pixSizeX == 0 && outputMapInfo != null)
            pixSizeX = outputMapInfo.getPixelSizeX();
        pixelSizeX.setText(String.valueOf(pixSizeX));

        float pixSizeY = (Float)(paramMap.get("pixelSizeY"));
        if(pixSizeY == 0 && outputMapInfo != null)
            pixSizeY = outputMapInfo.getPixelSizeY();
        pixelSizeY.setText(String.valueOf(pixSizeY));

        float eastVal = (Float)(paramMap.get("easting"));
        if(eastVal == 0 && outputMapInfo != null)
            eastVal = outputMapInfo.getEasting();
        easting.setText(String.valueOf(eastVal));

        float northVal = (Float)(paramMap.get("northing"));
        if(northVal == 0 && outputMapInfo != null)
            northVal = outputMapInfo.getNorthing();
        northing.setText(String.valueOf(northVal));

        float orienVal = (Float)(paramMap.get("orientation"));
        if(orienVal == 0 && outputMapInfo != null)
            orienVal = outputMapInfo.getOrientation();
        orientation.setText(String.valueOf(orienVal));
       
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(true, "");
    }

    @Override
    public void updateParameters() {

        OperatorUIUtils.updateBandList(bandList, paramMap);

        paramMap.put("projectionName", projectionName.getSelectedItem());
        paramMap.put("resamplingMethod", resamplingMethod.getSelectedItem());

        paramMap.put("pixelSizeX", Float.parseFloat(pixelSizeX.getText()));
        paramMap.put("pixelSizeY", Float.parseFloat(pixelSizeY.getText()));
        paramMap.put("easting", Float.parseFloat(easting.getText()));
        paramMap.put("northing", Float.parseFloat(northing.getText()));
        paramMap.put("orientation", Float.parseFloat(orientation.getText()));
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
        DialogUtils.addComponent(contentPane, gbc, "Resampling Method:", resamplingMethod);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Pixel Size X (deg):", pixelSizeX);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Pixel Size Y (deg):", pixelSizeY);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Easting (deg):", easting);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Northing (deg):", northing);
        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, "Orientation (deg):", orientation);
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