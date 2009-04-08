package org.esa.nest.dat.toolviews.nestwwview;

import com.bc.ceres.core.ProgressMonitor;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.SurfaceImage;
import gov.nasa.worldwind.util.Logging;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.ProductUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**

 */
public class SurfaceImageLayer extends RenderableLayer {
    private Product selectedProduct = null;

    private final ConcurrentHashMap<String, SurfaceImage> imageTable = new ConcurrentHashMap<String, SurfaceImage>();

    public String[] getProductNames() {
        return imageTable.keySet().toArray(new String[imageTable.size()]);
    }

    public void removeImage(String imagePath) {
        final SurfaceImage si = this.imageTable.get(imagePath);
        if (si != null) {
            this.removeRenderable(si);
            this.imageTable.remove(imagePath);
        }
    }

    @Override
    public void setOpacity(double opacity) {
        super.setOpacity(opacity);

        for (Map.Entry<String, SurfaceImage> entry : this.imageTable.entrySet()) {
            entry.getValue().setOpacity(opacity);
        }
    }

    public void setOpacity(String name, double opacity) {
        imageTable.get(name).setOpacity(opacity);
    }

    public double getOpacity(String name) {
        return imageTable.get(name).getOpacity();
    }

    public void setSelectedProduct(Product product) {
        selectedProduct = product;
    }

    public Product getSelectedProduct() {
        return selectedProduct;
    }

    public void addProduct(final Product product) throws IOException {
        final GeoCoding geoCoding = product.getGeoCoding();
        if (geoCoding == null) return;

        final String name = product.getName();
        if(this.imageTable.get(name) != null)
            return;

        final BufferedImage image = createQuickLook(product);
        final GeoPos geoPos1 = product.getGeoCoding().getGeoPos(new PixelPos(0, 0), null);
        final GeoPos geoPos2 = product.getGeoCoding().getGeoPos(new PixelPos(product.getSceneRasterWidth(),
                product.getSceneRasterHeight()), null);

        final Sector sector = new Sector(Angle.fromDegreesLatitude(geoPos1.getLat()),
                Angle.fromDegreesLatitude(geoPos2.getLat()),
                Angle.fromDegreesLongitude(geoPos1.getLon()),
                Angle.fromDegreesLongitude(geoPos2.getLon()));

        if (this.imageTable.contains(name))
            this.removeImage(name);

        final SurfaceImage si = new SurfaceImage(image, sector);
        si.setOpacity(this.getOpacity());
        this.addRenderable(si);
        this.imageTable.put(name, si);
    }

    public void removeProduct(final Product product) {
        removeImage(product.getName());
    }

    private static BufferedImage createQuickLook(final Product product) throws IOException {
        final ProductSubsetDef productSubsetDef = new ProductSubsetDef("subset");
        int scaleFactor = product.getSceneRasterWidth() / 1000;
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        productSubsetDef.setSubSampling(scaleFactor, scaleFactor);
        final Product productSubset = product.createSubset(productSubsetDef, null, null);

        final String quicklookBandName = ProductUtils.findSuitableQuicklookBandName(productSubset);
        final Band band = productSubset.getBand(quicklookBandName);
        return ProductUtils.createRgbImage(new RasterDataNode[] {band}, band.getImageInfo(ProgressMonitor.NULL), ProgressMonitor.NULL);
    }
}
