package org.esa.nest.dat.layersrc;

import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.RenderedImage;

import org.esa.beam.framework.datamodel.*;

/**
 * Shows the movement of GCP in a coregistered image
 *
 */
public class GCPVectorLayer extends Layer {

    private final Product product;
    private final Band band;
    private final float lineThickness = 2.0f;

    public GCPVectorLayer(ValueContainer configuration) {
        super(LayerType.getLayerType(GCPVectorLayerType.class.getName()), configuration);
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

        final int width = ri.getWidth();
        final int height = ri.getHeight();
        final Rectangle irect = ibounds.getBounds().intersection(new Rectangle(0, 0, width, height));
        if (irect.isEmpty()) {
            return;
        }

        final AffineTransform m2v = vp.getModelToViewTransform();

        final Graphics2D graphics = rendering.getGraphics();
        graphics.setStroke(new BasicStroke(lineThickness));

        final double[] ipts = new double[8];
        final double[] mpts = new double[8];
        final double[] vpts = new double[8];

        final ProductNodeGroup<Pin> slaveGCPGroup = product.getGcpGroup(band);
        final ProductNodeGroup<Pin> masterGCPGroup = product.getGcpGroup(masterBand);

        for(int i=0; i < slaveGCPGroup.getNodeCount(); ++i) {
            final Pin slaveGCP = slaveGCPGroup.get(i);
            final Pin masterGCP = masterGCPGroup.get(slaveGCP.getName());
            if(masterGCP == null)
                continue;

            createArrow((int)slaveGCP.getPixelPos().getX(),
                        (int)slaveGCP.getPixelPos().getY(),
                        (int)masterGCP.getPixelPos().getX(),
                        (int)masterGCP.getPixelPos().getY(), 5, ipts);

            i2m.transform(ipts, 0, mpts, 0, 4);
            m2v.transform(mpts, 0, vpts, 0, 4);

            graphics.setColor(Color.RED);
            graphics.draw(new Line2D.Double(vpts[0], vpts[1], vpts[2], vpts[3]));
            graphics.draw(new Line2D.Double(vpts[4], vpts[5], vpts[2], vpts[3]));
            graphics.draw(new Line2D.Double(vpts[6], vpts[7], vpts[2], vpts[3]));
        }
    }

    public static void createArrow(int x, int y, int xx, int yy, int i1, double[] ipts)
    {
        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = xx;
        ipts[3] = yy;
        final double d = xx - x;
        final double d1 = -(yy - y);
        final double d2 = Math.sqrt(d * d + d1 * d1);
        final double d3;
        if(d2 > (3.0 * i1))
            d3 = i1;
        else
            d3 = d2 / 3.0;
        if(d2 >= 1.0) {
            final double d4 = (d3 * d) / d2;
            final double d5 = -((d3 * d1) / d2);
            final double d6 = (double)xx - 3.0 * d4;
            final double d7 = (double)yy - 3.0 * d5;
            ipts[4] = (int)(d6 - d5);
            ipts[5] = (int)(d7 + d4);
            ipts[6] = (int)(d6 + d5);
            ipts[7] = (int)(d7 - d4);
        }
    }
}