/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dat.layersrc;

import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerTypeRegistry;
import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;
import org.esa.beam.framework.datamodel.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.RenderedImage;

/**
 * Shows the movement of GCP in a coregistered image
 *
 */
public class GCPVectorLayer extends Layer {

    private final Product product;
    private final Band band;
    private final float lineThickness = 4.0f;

    public GCPVectorLayer(PropertySet configuration) {
        super(LayerTypeRegistry.getLayerType(GCPVectorLayerType.class.getName()), configuration);
        setName("GCP Movement");
        product = (Product) configuration.getValue("product");
        band = (Band) configuration.getValue("band");
    }

    @Override
    protected void renderLayer(Rendering rendering) {

        final Band masterBand = product.getBandAt(0);
        if(band == null || masterBand == band)
            return;

        final Viewport vp = rendering.getViewport();
        final int level = 0;

        final RasterDataNode raster = band;
        final MultiLevelImage mli = raster.getGeophysicalImage();

        final AffineTransform m2i = mli.getModel().getModelToImageTransform(level);
        final AffineTransform i2m = mli.getModel().getImageToModelTransform(level);

        final Shape vbounds = vp.getViewBounds();
        final Shape mbounds = vp.getViewToModelTransform().createTransformedShape(vbounds);
        final Shape ibounds = m2i.createTransformedShape(mbounds);

        final RenderedImage ri = mli.getImage(level);
        final Rectangle irect = ibounds.getBounds().intersection(new Rectangle(0, 0, ri.getWidth(), ri.getHeight()));
        if (irect.isEmpty()) {
            return;
        }
        double zoom = rendering.getViewport().getZoomFactor();
        System.out.println("zoom "+zoom);
        
        final AffineTransform m2v = vp.getModelToViewTransform();

        final Graphics2D graphics = rendering.getGraphics();
        graphics.setStroke(new BasicStroke(lineThickness));

        final double[] ipts = new double[8];
        final double[] mpts = new double[8];
        final double[] vpts = new double[8];

        final ProductNodeGroup<Placemark> slaveGCPGroup = product.getGcpGroup(band);
        final ProductNodeGroup<Placemark> masterGCPGroup = product.getGcpGroup(masterBand);

        for(int i=0; i < slaveGCPGroup.getNodeCount(); ++i) {
            final Placemark slaveGCP = slaveGCPGroup.get(i);
            final Placemark masterGCP = masterGCPGroup.get(slaveGCP.getName());
            if(masterGCP == null)
                continue;

            createArrow((int)slaveGCP.getPixelPos().getX(),
                        (int)slaveGCP.getPixelPos().getY(),
                        (int)masterGCP.getPixelPos().getX(),
                        (int)masterGCP.getPixelPos().getY(), 5, ipts, zoom);

            i2m.transform(ipts, 0, mpts, 0, 4);
            m2v.transform(mpts, 0, vpts, 0, 4);

            graphics.setColor(Color.RED);
            graphics.draw(new Line2D.Double(vpts[4], vpts[5], vpts[2], vpts[3]));
            graphics.draw(new Line2D.Double(vpts[6], vpts[7], vpts[2], vpts[3]));
            graphics.setColor(Color.RED);
            graphics.draw(new Line2D.Double(vpts[0], vpts[1], vpts[2], vpts[3]));
        }
    }

    private static void createArrow(int x, int y, int xx, int yy, int i1, double[] ipts, double zoom)
    {
        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = xx;
        ipts[3] = yy;
        final double d = xx - x;
        final double d1 = -(yy - y);
        double mult = 5/zoom;
        if(zoom > 2)
            mult = 1;
        double d2 = Math.sqrt(d * d + d1 * d1);
        final double d3;
        final double size = 3.0;
        if(d2 > (3.0 * i1))
            d3 = i1;
        else
            d3 = d2 / 3.0;
        if(d2 < 1.0)
            d2 = 1.0;
        if(d2 >= 1.0) {
            final double d4 = (d3 * d) / d2;
            final double d5 = -((d3 * d1) / d2);
            final double d6 = (double)xx - size * d4 * mult;
            final double d7 = (double)yy - size * d5 * mult;
            ipts[4] = (int)(d6 - d5);
            ipts[5] = (int)(d7 + d4);
            ipts[6] = (int)(d6 + d5);
            ipts[7] = (int)(d7 - d4);
        }
    }
}