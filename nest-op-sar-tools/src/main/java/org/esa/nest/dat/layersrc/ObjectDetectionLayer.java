package org.esa.nest.dat.layersrc;

import com.bc.ceres.binding.ValueContainer;
import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.RenderedImage;

import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;

/**
 * Shows a detected object
 *
 */
public class ObjectDetectionLayer extends Layer {

    private final Product product;
    private final Band band;

    private final Color[] palette;
    private float lineThickness = 2.0f;

    public ObjectDetectionLayer(ValueContainer configuration) {
        super(LayerType.getLayerType(ObjectDetectionLayerType.class.getName()), configuration);
        product = (Product) configuration.getValue("product");
        band = (Band) configuration.getValue("band");

        palette = new Color[256];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = new Color(i, i, i);
        }
    }

    @Override
    protected void renderLayer(Rendering rendering) {

        if(band == null)
            return;

        final Viewport vp = rendering.getViewport();
        final int level = 0;

        final RasterDataNode raster = product.getRasterDataNode(product.getBandAt(0).getName());
        final MultiLevelImage mli = raster.getGeophysicalImage();

        final AffineTransform m2i = mli.getModel().getModelToImageTransform(level);
        final AffineTransform i2m = mli.getModel().getImageToModelTransform(level);

        final Shape vbounds = vp.getViewBounds();
        final Shape mbounds = vp.getViewToModelTransform().createTransformedShape(vbounds);
        final Shape ibounds = m2i.createTransformedShape(mbounds);

        final RenderedImage winduRI = mli.getImage(level);

        final int width = winduRI.getWidth();
        final int height = winduRI.getHeight();
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


        final double length = 5;
        int x = 400;
        int y = 400;

        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = x + 100;
        ipts[3] = y + 100;
        ipts[4] = x - 50;
        ipts[5] = y - 50;
        ipts[6] = x + 10;
        ipts[7] = y + 50;
        i2m.transform(ipts, 0, mpts, 0, 4);
        m2v.transform(mpts, 0, vpts, 0, 4);

        graphics.setColor(Color.RED);
        graphics.draw(new Line2D.Double(vpts[0], vpts[1], vpts[2], vpts[3]));
        graphics.draw(new Line2D.Double(vpts[4], vpts[5], vpts[2], vpts[3]));
        graphics.draw(new Line2D.Double(vpts[6], vpts[7], vpts[2], vpts[3]));

    }
}