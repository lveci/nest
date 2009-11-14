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
import org.esa.nest.gpf.oceantools.WindFieldEstimationOp;
import org.esa.nest.util.XMLSupport;
import org.jdom.Attribute;
import org.jdom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a detected object
 *
 */
public class WindFieldEstimationLayer extends Layer {

    private final Product product;
    private final Band band;

    private final List<WindFieldEstimationOp.WindFieldRecord> targetList = new ArrayList<WindFieldEstimationOp.WindFieldRecord>();
    private final float lineThickness = 2.0f;

    public WindFieldEstimationLayer(PropertyContainer configuration) {
        super(LayerTypeRegistry.getLayerType(WindFieldEstimationLayerType.class.getName()), configuration);
        product = (Product) configuration.getValue("product");
        band = (Band) configuration.getValue("band");

        LoadTargets(getWindFieldReportFile(product));
    }

    public static File getWindFieldReportFile(final Product product) {
        final MetadataElement root = product.getMetadataRoot();
        if (root != null) {
            final MetadataElement absMetadata = AbstractMetadata.getAbstractedMetadata(product);
            if (absMetadata != null) {
                final String windFieldReportFilePath = absMetadata.getAttributeString(AbstractMetadata.wind_field_report_file, null);
                if(windFieldReportFilePath != null) {
                    final File file = new File(windFieldReportFilePath);
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
                if(targetsDetectedElem.getName().equals("windFieldEstimated")) {
                    final Attribute attrib = targetsDetectedElem.getAttribute("bandName");
                    if(attrib != null && band.getName().equalsIgnoreCase(attrib.getValue())) {
                        final List content = targetsDetectedElem.getContent();
                        for (Object det : content) {
                            if (det instanceof Element) {
                                final Element targetElem = (Element) det;
                                if(targetElem.getName().equals("windFieldInfo")) {
                                    final Attribute lat = targetElem.getAttribute("lat");
                                    if(lat == null) continue;
                                    final Attribute lon = targetElem.getAttribute("lon");
                                    if(lon == null) continue;
                                    final Attribute speed = targetElem.getAttribute("speed");
                                    if(speed == null) continue;
                                    final Attribute dx = targetElem.getAttribute("dx");
                                    if(dx == null) continue;
                                    final Attribute dy = targetElem.getAttribute("dy");
                                    if(dy == null) continue;

                                    targetList.add(new WindFieldEstimationOp.WindFieldRecord(
                                            Double.parseDouble(lat.getValue()),
                                            Double.parseDouble(lon.getValue()),
                                            Double.parseDouble(speed.getValue()),
                                            Double.parseDouble(dx.getValue()),
                                            Double.parseDouble(dy.getValue())));
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
        final double[] ipts = new double[8];
        final double[] mpts = new double[8];
        final double[] vpts = new double[8];

        double arrowSize = 10.0;
        if (targetList.size() > 1) {
            WindFieldEstimationOp.WindFieldRecord target0 = targetList.get(0);
            geo.setLocation((float)target0.lat, (float)target0.lon);
            geoCoding.getPixelPos(geo, pix);
            float x0 = pix.x;
            float y0 = pix.y;
            WindFieldEstimationOp.WindFieldRecord target1 = targetList.get(1);
            geo.setLocation((float)target1.lat, (float)target1.lon);
            geoCoding.getPixelPos(geo, pix);
            float x1 = pix.x;
            float y1 = pix.y;
            arrowSize = Math.sqrt((x0 - x1)*(x0 - x1) + (y0 - y1)*(y0 - y1))/2.5;
        }

        for(WindFieldEstimationOp.WindFieldRecord target : targetList) {

            geo.setLocation((float)target.lat, (float)target.lon);
            geoCoding.getPixelPos(geo, pix);
            double dx = arrowSize*target.dx;
            double dy = arrowSize*target.dy;

            ipts[0] = pix.getX();
            ipts[1] = pix.getY();
            ipts[2] = ipts[0] + dx;
            ipts[3] = ipts[1] + dy;

            ipts[4] = ipts[2] - (1.732*dx - dy)/6;
            ipts[5] = ipts[3] - (1.732*dy + dx)/6;
            ipts[6] = ipts[2] - (1.732*dx + dy)/6;
            ipts[7] = ipts[3] - (1.732*dy - dx)/6;

            i2m.transform(ipts, 0, mpts, 0, 4);
            m2v.transform(mpts, 0, vpts, 0, 4);

            graphics.setColor(Color.RED);
            graphics.draw(new Line2D.Double(vpts[0], vpts[1], vpts[2], vpts[3]));
            graphics.draw(new Line2D.Double(vpts[4], vpts[5], vpts[2], vpts[3]));
            graphics.draw(new Line2D.Double(vpts[6], vpts[7], vpts[2], vpts[3]));
        }
    }
}