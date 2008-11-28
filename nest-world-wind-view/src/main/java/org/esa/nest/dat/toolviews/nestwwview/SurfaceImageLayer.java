package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwind.formats.tiff.*;
import gov.nasa.worldwind.util.*;

import javax.imageio.*;
import javax.imageio.spi.*;
import javax.media.jai.RasterFactory;
import javax.media.jai.PlanarImage;
import java.awt.image.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.util.ProductUtils;
import com.bc.ceres.core.ProgressMonitor;

/**
 * @author tag
 * @version $Id: SurfaceImageLayer.java,v 1.1 2008-11-28 20:16:50 lveci Exp $
 */
public class SurfaceImageLayer extends RenderableLayer
{
    Product selectedProduct = null;

    static
    {
        //IIORegistry reg = IIORegistry.getDefaultInstance();
        //reg.registerServiceProvider(GeotiffImageReaderSpi.inst());
    }

    private ConcurrentHashMap<String, SurfaceImage> imageTable = new ConcurrentHashMap<String, SurfaceImage>();

    public void addImage(String imagePath) throws IOException
    {
        if (imagePath == null)
        {
            String message = Logging.getMessage("nullValue.ImageSourceIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        File imageFile = new File(imagePath);
        BufferedImage image = ImageIO.read(imageFile);

        Sector sector = Sector.fromDegrees(40, 40, 60, 60);

        if (this.imageTable.contains(imagePath))
            this.removeImage(imagePath);

        SurfaceImage si = new SurfaceImage(image, sector);
        si.setOpacity(this.getOpacity());
        this.addRenderable(si);
        this.imageTable.put(imagePath, si);
    }

    public void removeImage(String imagePath)
    {
        SurfaceImage si = this.imageTable.get(imagePath);
        if (si != null)
        {
            this.removeRenderable(si);
            this.imageTable.remove(imagePath);
        }
    }

    @Override
    public void setOpacity(double opacity)
    {
        super.setOpacity(opacity);
        
        for (Map.Entry<String, SurfaceImage> entry : this.imageTable.entrySet())
        {
            entry.getValue().setOpacity(opacity);
        }
    }


    public void setSelectedProduct(Product product) {
        selectedProduct = product;
    }

    public Product getSelectedProduct() {
        return selectedProduct;
    }

    public void setProducts(Product[] products) {
        if(products == null)
            return;
        
        for(Product prod : products) {
            addProduct(prod);
        }
    }

    public void addProduct(Product product)
    {
        try {
            String name = product.getName();

            BufferedImage image = createQuickLook(product);
                GeoPos geoPos1 = product.getGeoCoding().getGeoPos(new PixelPos(0,0), null);
            GeoPos geoPos2 = product.getGeoCoding().getGeoPos(new PixelPos(product.getSceneRasterWidth(),
                                                                           product.getSceneRasterHeight()), null);

            Sector sector = new Sector(Angle.fromDegreesLatitude(geoPos1.getLat()),
                                       Angle.fromDegreesLatitude(geoPos2.getLat()),
                                       Angle.fromDegreesLongitude(geoPos1.getLon()),
                                       Angle.fromDegreesLongitude(geoPos2.getLon()));

            if (this.imageTable.contains(name))
                this.removeImage(name);

            SurfaceImage si = new SurfaceImage(image, sector);
            si.setOpacity(this.getOpacity());
            this.addRenderable(si);
            this.imageTable.put(name, si);

        } catch(IOException e) {

        }
    }

    private BufferedImage createQuickLook(final Product product) throws IOException {
        final ProductSubsetDef productSubsetDef = new ProductSubsetDef("subset");
        int scaleFactor = product.getSceneRasterWidth() / 500;
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        productSubsetDef.setSubSampling(scaleFactor, scaleFactor);
        final Product productSubset = product.createSubset(productSubsetDef, null, null);

        final String quicklookBandName = ProductUtils.findSuitableQuicklookBandName(productSubset);
        return ProductUtils.createColorIndexedImage(productSubset.getBand(quicklookBandName), ProgressMonitor.NULL);
    }
}
