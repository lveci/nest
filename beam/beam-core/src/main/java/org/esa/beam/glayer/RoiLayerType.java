package org.esa.beam.glayer;

import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.core.Assert;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerContext;
import com.bc.ceres.glayer.Style;
import com.bc.ceres.glayer.support.ImageLayer;
import com.bc.ceres.glevel.MultiLevelSource;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.glevel.RoiImageMultiLevelSource;

import java.awt.Color;
import java.awt.geom.AffineTransform;


/**
 * @author Marco Peters
 * @version $ Revision: $ Date: $
 * @since BEAM 4.6
 */
public class RoiLayerType extends ImageLayer.Type {

    public static final String ROI_LAYER_ID = "org.esa.beam.layers.roi";
    public static final String PROPERTY_COLOR = "roiOverlay.color";
    public static final String PROPERTY_TRANSPARENCY = "roiOverlay.transparency";
    public static final String PROPERTY_REFERENCED_RASTER = "roiOverlay.referencedRaster";
    public static final String PROPERTY_IMAGE_TO_MODEL_TRANSFORM = "roiOverlay.imageToModelTransform";

    @Override
    public String getName() {
        return "ROI";
    }

    @Override
    protected Layer createLayerImpl(LayerContext ctx, ValueContainer configuration) {
        final Color color = (Color) configuration.getValue(PROPERTY_COLOR);
        Assert.notNull(color, PROPERTY_COLOR);
        final RasterDataNode raster = (RasterDataNode) configuration.getValue(PROPERTY_REFERENCED_RASTER);
        Assert.notNull(raster, PROPERTY_REFERENCED_RASTER);
        final Double transparency = (Double) configuration.getValue(PROPERTY_TRANSPARENCY);
        Assert.notNull(transparency, PROPERTY_TRANSPARENCY);
        final AffineTransform i2mTransform = (AffineTransform) configuration.getValue(
                PROPERTY_IMAGE_TO_MODEL_TRANSFORM);
        Assert.notNull(i2mTransform, PROPERTY_IMAGE_TO_MODEL_TRANSFORM);

        if (configuration.getValue(ImageLayer.PROPERTY_NAME_MULTI_LEVEL_SOURCE) == null) {
            final MultiLevelSource multiLevelSource;
            if (raster.getROIDefinition() != null && raster.getROIDefinition().isUsable()) {
                multiLevelSource = RoiImageMultiLevelSource.create(raster, color, i2mTransform);
            } else {
                multiLevelSource = MultiLevelSource.NULL;
            }
            try {
                configuration.setValue(ImageLayer.PROPERTY_NAME_MULTI_LEVEL_SOURCE, multiLevelSource);
            } catch (ValidationException e) {
                throw new IllegalArgumentException(e);
            }
        }

        final ImageLayer roiLayer = new ImageLayer(this, configuration);
        roiLayer.setName("ROI");
        roiLayer.setId(ROI_LAYER_ID);
        roiLayer.setVisible(false);
        roiLayer.getStyle().setOpacity(1 - transparency);
        configureLayer(configuration, roiLayer);

        return roiLayer;
    }

    @Override
    public ValueContainer getConfigurationTemplate() {
        final ValueContainer template = super.getConfigurationTemplate();
        template.addModel(createDefaultValueModel(PROPERTY_REFERENCED_RASTER, RasterDataNode.class));
        template.getDescriptor(PROPERTY_REFERENCED_RASTER).setNotNull(true);

        template.addModel(createDefaultValueModel(PROPERTY_COLOR, Color.class));
        template.getDescriptor(PROPERTY_COLOR).setNotNull(true);

        template.addModel(createDefaultValueModel(PROPERTY_TRANSPARENCY, Double.class));
        template.getDescriptor(PROPERTY_TRANSPARENCY).setNotNull(true);

        template.addModel(createDefaultValueModel(PROPERTY_IMAGE_TO_MODEL_TRANSFORM, AffineTransform.class));
        template.getDescriptor(PROPERTY_IMAGE_TO_MODEL_TRANSFORM).setNotNull(true);

        return template;
    }

    private void configureLayer(ValueContainer configuration, Layer layer) {

        Color color = (Color) configuration.getValue(PROPERTY_COLOR);
        Double transparency = (Double) configuration.getValue(PROPERTY_TRANSPARENCY);
        RasterDataNode raster = (RasterDataNode) configuration.getValue(PROPERTY_REFERENCED_RASTER);
        AffineTransform i2mTransform = (AffineTransform) configuration.getValue(PROPERTY_IMAGE_TO_MODEL_TRANSFORM);
        if (transparency == null) {
            transparency = 0.0;
        }
        Style style = layer.getStyle();
        style.setProperty(PROPERTY_COLOR, color);
        style.setOpacity(1.0 - transparency);
        style.setProperty(PROPERTY_COLOR, color);
        style.setProperty(PROPERTY_REFERENCED_RASTER, raster);
        style.setProperty(PROPERTY_IMAGE_TO_MODEL_TRANSFORM, i2mTransform);

        style.setComposite(layer.getStyle().getComposite());

        layer.setStyle(style);
    }
}