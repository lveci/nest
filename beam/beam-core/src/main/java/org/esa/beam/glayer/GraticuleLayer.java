/*
 * $Id: GraticuleLayer.java,v 1.2 2009-05-12 12:56:42 lveci Exp $
 *
 * Copyright (C) 2008 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.glayer;

import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.Style;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;
import org.esa.beam.framework.datamodel.Graticule;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNodeEvent;
import org.esa.beam.framework.datamodel.ProductNodeListenerAdapter;
import org.esa.beam.framework.datamodel.RasterDataNode;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;

/**
 * @author Marco Zuehlke
 * @version $Revision: 1.2 $ $Date: 2009-05-12 12:56:42 $
 * @since BEAM 4.2
 */
public class GraticuleLayer extends Layer {

    private static final GraticuleLayerType LAYER_TYPE = (GraticuleLayerType) LayerType.getLayerType(
            GraticuleLayerType.class.getName());

    private RasterDataNode raster;
    private final AffineTransform i2mTransform;

    private ProductNodeHandler productNodeHandler;
    private Graticule graticule;

    public GraticuleLayer(RasterDataNode raster, AffineTransform i2mTransform) {
        this(LAYER_TYPE, initConfiguration(LAYER_TYPE.getConfigurationTemplate(), raster, i2mTransform));
    }


    public GraticuleLayer(GraticuleLayerType type, ValueContainer configuration) {
        super(type, configuration);
        this.i2mTransform = (AffineTransform) getConfiguration().getValue(GraticuleLayerType.PROPERTY_NAME_TRANSFORM);
        this.raster = (RasterDataNode) getConfiguration().getValue(GraticuleLayerType.PROPERTY_NAME_RASTER);

        productNodeHandler = new ProductNodeHandler();
        raster.getProduct().addProductNodeListener(productNodeHandler);

        setTransparency(0.5);
    }

    private static ValueContainer initConfiguration(ValueContainer configurationTemplate, RasterDataNode raster,
                                                    AffineTransform i2mTransform) {
        try {
            configurationTemplate.setValue(GraticuleLayerType.PROPERTY_NAME_RASTER, raster);
            configurationTemplate.setValue(GraticuleLayerType.PROPERTY_NAME_TRANSFORM, i2mTransform);
        } catch (ValidationException e) {
            throw new IllegalArgumentException(e);
        }
        return configurationTemplate;
    }

    private Product getProduct() {
        return getRaster().getProduct();
    }

    RasterDataNode getRaster() {
        return raster;
    }

    AffineTransform getI2mTransform() {
        return i2mTransform;
    }

    @Override
    public void renderLayer(Rendering rendering) {
        if (graticule == null) {
            graticule = Graticule.create(raster,
                                         getResAuto(),
                                         getResPixels(),
                                         (float) getResLat(),
                                         (float) getResLon());
        }
        if (graticule != null) {
            final Graphics2D g2d = rendering.getGraphics();
            final Viewport vp = rendering.getViewport();
            final AffineTransform transformSave = g2d.getTransform();
            try {
                final AffineTransform transform = new AffineTransform();
                transform.concatenate(transformSave);
                transform.concatenate(vp.getModelToViewTransform());
                transform.concatenate(i2mTransform);
                g2d.setTransform(transform);
                final GeneralPath[] linePaths = graticule.getLinePaths();
                if (linePaths != null) {
                    drawLinePaths(g2d, linePaths);
                }
                if (isTextEnabled()) {
                    final Graticule.TextGlyph[] textGlyphs = graticule.getTextGlyphs();
                    if (textGlyphs != null) {
                        drawTextLabels(g2d, textGlyphs);
                    }
                }
            } finally {
                g2d.setTransform(transformSave);
            }
        }
    }

    private void drawLinePaths(Graphics2D g2d, final GeneralPath[] linePaths) {
        Composite oldComposite = null;
        if (getLineTransparency() > 0.0) {
            oldComposite = g2d.getComposite();
            g2d.setComposite(getAlphaComposite(getLineTransparency()));
        }
        g2d.setPaint(getLineColor());
        g2d.setStroke(new BasicStroke((float) getLineWidth()));
        for (GeneralPath linePath : linePaths) {
            g2d.draw(linePath);
        }
        if (oldComposite != null) {
            g2d.setComposite(oldComposite);
        }
    }

    private void drawTextLabels(Graphics2D g2d, final Graticule.TextGlyph[] textGlyphs) {
        final float tx = 3;
        final float ty = -3;

        if (getTextBgTransparency() < 1.0) {
            Composite oldComposite = null;
            if (getTextBgTransparency() > 0.0) {
                oldComposite = g2d.getComposite();
                g2d.setComposite(getAlphaComposite(getTextBgTransparency()));
            }

            g2d.setPaint(getTextBgColor());
            g2d.setStroke(new BasicStroke(0));
            for (Graticule.TextGlyph glyph : textGlyphs) {
                g2d.translate(glyph.getX(), glyph.getY());
                g2d.rotate(glyph.getAngle());

                Rectangle2D labelBounds = g2d.getFontMetrics().getStringBounds(glyph.getText(), g2d);
                labelBounds.setRect(labelBounds.getX() + tx - 1,
                                    labelBounds.getY() + ty - 1,
                                    labelBounds.getWidth() + 4,
                                    labelBounds.getHeight());
                g2d.fill(labelBounds);

                g2d.rotate(-glyph.getAngle());
                g2d.translate(-glyph.getX(), -glyph.getY());
            }

            if (oldComposite != null) {
                g2d.setComposite(oldComposite);
            }
        }

        g2d.setFont(getTextFont());
        g2d.setPaint(getTextFgColor());
        for (Graticule.TextGlyph glyph : textGlyphs) {
            g2d.translate(glyph.getX(), glyph.getY());
            g2d.rotate(glyph.getAngle());

            g2d.drawString(glyph.getText(), tx, ty);

            g2d.rotate(-glyph.getAngle());
            g2d.translate(-glyph.getX(), -glyph.getY());
        }
    }

    private AlphaComposite getAlphaComposite(double itemTransparancy) {
        double combinedAlpha = (1.0 - getTransparency()) * (1.0 - itemTransparancy);
        return AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) combinedAlpha);
    }

    @Override
    public void disposeLayer() {
        final Product product = getProduct();
        if (product != null) {
            product.removeProductNodeListener(productNodeHandler);
            graticule = null;
            raster = null;
        }
    }

    @Override
    protected void fireLayerPropertyChanged(PropertyChangeEvent event) {
        String propertyName = event.getPropertyName();
        if (propertyName.equals(GraticuleLayerType.PROPERTY_NAME_RES_AUTO) ||
            propertyName.equals(GraticuleLayerType.PROPERTY_NAME_RES_LAT) ||
            propertyName.equals(GraticuleLayerType.PROPERTY_NAME_RES_LON) ||
            propertyName.equals(GraticuleLayerType.PROPERTY_NAME_RES_PIXELS)) {
            graticule = null;
        }
        if (getConfiguration().getModel(propertyName) != null) {
            try {
                getConfiguration().setValue(propertyName, event.getNewValue());
            } catch (ValidationException e) {
                throw new IllegalArgumentException(e);
            }
        }
        super.fireLayerPropertyChanged(event);
    }

    private boolean getResAuto() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_RES_AUTO,
                                        GraticuleLayerType.DEFAULT_RES_AUTO);
    }

    private double getResLon() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_RES_LON,
                                        GraticuleLayerType.DEFAULT_RES_LON);
    }

    private double getResLat() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_RES_LAT,
                                        GraticuleLayerType.DEFAULT_RES_LAT);
    }

    private int getResPixels() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_RES_PIXELS,
                                        GraticuleLayerType.DEFAULT_RES_PIXELS);
    }

    private boolean isTextEnabled() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_TEXT_ENABLED,
                                        GraticuleLayerType.DEFAULT_TEXT_ENABLED);
    }

    private Color getLineColor() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_LINE_COLOR,
                                        GraticuleLayerType.DEFAULT_LINE_COLOR);
    }

    private double getLineTransparency() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_LINE_TRANSPARENCY,
                                        GraticuleLayerType.DEFAULT_LINE_TRANSPARENCY);
    }

    private double getLineWidth() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_LINE_WIDTH,
                                        GraticuleLayerType.DEFAULT_LINE_WIDTH);
    }

    private Font getTextFont() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_TEXT_FONT,
                                        GraticuleLayerType.DEFAULT_TEXT_FONT);
    }

    private Color getTextFgColor() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_TEXT_FG_COLOR,
                                        GraticuleLayerType.DEFAULT_TEXT_FG_COLOR);
    }

    private Color getTextBgColor() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_TEXT_BG_COLOR,
                                        GraticuleLayerType.DEFAULT_TEXT_BG_COLOR);
    }

    private double getTextBgTransparency() {
        return getConfigurationProperty(GraticuleLayerType.PROPERTY_NAME_TEXT_BG_TRANSPARENCY,
                                        GraticuleLayerType.DEFAULT_TEXT_BG_TRANSPARENCY);
    }

    private class ProductNodeHandler extends ProductNodeListenerAdapter {

        /**
         * Overwrite this method if you want to be notified when a node changed.
         *
         * @param event the product node which the listener to be notified
         */
        @Override
        public void nodeChanged(ProductNodeEvent event) {
            if (event.getSourceNode() == getProduct() && Product.PROPERTY_NAME_GEOCODING.equals(
                    event.getPropertyName())) {
                // Force recreation
                graticule = null;
                fireLayerDataChanged(getModelBounds());
            }
        }
    }

}
