package org.esa.nest.dat.layersrc;

import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.LayerType;
import com.bc.ceres.glayer.LayerTypeRegistry;
import com.bc.ceres.glevel.MultiLevelImage;
import com.bc.ceres.grender.Rendering;
import com.bc.ceres.grender.Viewport;
import com.bc.ceres.binding.PropertyContainer;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.gpf.oceantools.ObjectDiscriminationOp;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a detected object
 *
 */
public class ObjectDetectionLayer extends Layer {

    private final Product product;
    private final Band band;

    private final List<ObjectDiscriminationOp.ShipRecord> targetList = new ArrayList<ObjectDiscriminationOp.ShipRecord>();
    private double rangeSpacing;
    private double azimuthSpacing;
    private final float lineThickness = 2.0f;
    private final double border = 5.0;

    public ObjectDetectionLayer(PropertyContainer configuration) {
        super(LayerTypeRegistry.getLayerType(ObjectDetectionLayerType.class.getName()), configuration);
        product = (Product) configuration.getValue("product");
        band = (Band) configuration.getValue("band");

        getPixelSize();

        LoadTargets(getTargetFile(product));
    }

    private void getPixelSize() {
        final MetadataElement root = product.getMetadataRoot();
        if (root != null) {
            final MetadataElement absMetadata = AbstractMetadata.getAbstractedMetadata(product);
            if (absMetadata != null) {
                rangeSpacing = absMetadata.getAttributeDouble(AbstractMetadata.range_spacing, 0);
                azimuthSpacing = absMetadata.getAttributeDouble(AbstractMetadata.azimuth_spacing, 0);
            }
        }
    }

    public static File getTargetFile(final Product product) {
        final MetadataElement root = product.getMetadataRoot();
        if (root != null) {
            final MetadataElement absMetadata = AbstractMetadata.getAbstractedMetadata(product);
            if (absMetadata != null) {
                final String shipFilePath = absMetadata.getAttributeString(AbstractMetadata.target_report_file, null);
                if(shipFilePath != null) {
                    final File file = new File(shipFilePath);
                    if(file.exists())
                        return file;
                }
            }
        }
        return null;
    }

    private void LoadTargets(final File file) {
        if(file == null)
            return;

        org.jdom.Document doc;
        try {
            doc = XMLSupport.LoadXML(file.getAbsolutePath());
        } catch(IOException e) {
            return;
        }

        targetList.clear();

        final Element root = doc.getRootElement();

        final List children = root.getContent();
        for (Object aChild : children) {
            if (aChild instanceof Element) {
                final Element targetsDetectedElem = (Element) aChild;
                if(targetsDetectedElem.getName().equals("targetsDetected")) {
                    final Attribute attrib = targetsDetectedElem.getAttribute("bandName");
                    if(attrib != null && band.getName().equalsIgnoreCase(attrib.getValue())) {
                        final List content = targetsDetectedElem.getContent();
                        for (Object det : content) {
                            if (det instanceof Element) {
                                final Element targetElem = (Element) det;
                                if(targetElem.getName().equals("target")) {
                                    final Attribute lat = targetElem.getAttribute("lat");
                                    if(lat == null) continue;
                                    final Attribute lon = targetElem.getAttribute("lon");
                                    if(lon == null) continue;
                                    final Attribute width = targetElem.getAttribute("width");
                                    if(width == null) continue;
                                    final Attribute length = targetElem.getAttribute("length");
                                    if(length == null) continue;
                                    final Attribute intensity = targetElem.getAttribute("intensity");
                                    if(intensity == null) continue;

                                    targetList.add(new ObjectDiscriminationOp.ShipRecord(
                                            Double.parseDouble(lat.getValue()),
                                            Double.parseDouble(lon.getValue()),
                                            (Double.parseDouble(width.getValue()) / rangeSpacing) + border,
                                            (Double.parseDouble(length.getValue()) / azimuthSpacing) + border,
                                            Double.parseDouble(intensity.getValue())));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void renderLayer(Rendering rendering) {

        if(band == null || targetList.isEmpty())
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

        final GeoCoding geoCoding = product.getGeoCoding();
        final GeoPos geo = new GeoPos();
        final PixelPos pix = new PixelPos();

        final Graphics2D graphics = rendering.getGraphics();
        graphics.setStroke(new BasicStroke(lineThickness));
        graphics.setColor(Color.RED);

        final AffineTransform m2v = vp.getModelToViewTransform();
        final double[] ipts = new double[4];
        final double[] mpts = new double[4];
        final double[] vpts = new double[4];

        for(ObjectDiscriminationOp.ShipRecord target : targetList) {
            geo.setLocation((float)target.lat, (float)target.lon);
            geoCoding.getPixelPos(geo, pix);
            final double halfWidth = target.width/2.0;
            final double halfHeight = target.length/2.0;

            ipts[0] = pix.getX()-halfWidth;
            ipts[1] = pix.getY()-halfHeight;
            ipts[2] = ipts[0]+target.width;
            ipts[3] = ipts[1]+target.length;
            i2m.transform(ipts, 0, mpts, 0, 2);
            m2v.transform(mpts, 0, vpts, 0, 2);

            final double w = vpts[2]-vpts[0];
            final double h = vpts[3]-vpts[1];
            final Ellipse2D.Double circle = new Ellipse2D.Double(vpts[0], vpts[1], w, h);
            graphics.draw(circle);
        }
    }
}