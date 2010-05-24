package org.esa.nest.db;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.util.ProductUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.ResourceUtils;

import javax.imageio.ImageIO;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 Generates Quicklooks
 */
public class QuickLookGenerator {

    private static final String QUICKLOOK_PREFIX = "QL_";
    private static final String QUICKLOOK_EXT = ".jpg";
    private static final int MAX_WIDTH = 400;

    private static final File dbStorageDir = new File(ResourceUtils.getApplicationUserDir(true),
                                                      ProductDB.DEFAULT_PRODUCT_DATABASE_NAME+
                                                      File.separator + "QuickLooks");

    public static boolean quickLookExists(final ProductEntry entry) {
        final File quickLookFile = getQuickLookFile(dbStorageDir, entry.getId());
        return quickLookFile.exists() && quickLookFile.length() > 0;
    }

    public static BufferedImage loadQuickLook(final ProductEntry entry) {
        final File quickLookFile = getQuickLookFile(dbStorageDir, entry.getId());
        BufferedImage bufferedImage = null;
        if(quickLookFile.exists() && quickLookFile.length() > 0) {
            bufferedImage = loadFile(quickLookFile);
        }
        return bufferedImage;
    }

    public static void deleteQuickLook(final int id) {
        final File quickLookFile = getQuickLookFile(dbStorageDir, id);
        if(quickLookFile.exists())
            quickLookFile.delete();
    }

    private static File getQuickLookFile(final File storageDir, final int id) {
        return new File(storageDir, QUICKLOOK_PREFIX + id + QUICKLOOK_EXT);
    }

    private static BufferedImage loadFile(final File file) {
        BufferedImage bufferedImage = null;
        if (file.canRead()) {
            try {
                final FileInputStream fis = new FileInputStream(file);
                try {
                    bufferedImage = ImageIO.read(fis);
                } finally {
                    fis.close();
                }
            } catch(Exception e) {
                //
            }
        }
        return bufferedImage;
    }

    private static boolean isComplex(Product product) {
        //!todo replace with entry.getSampleType

        final MetadataElement root = product.getMetadataRoot();
        if(root != null) {
            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
            if(absRoot != null && absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE, "").equals("COMPLEX"))
                return true;
        }
        return false;
    }

    private static BufferedImage createQuickLookImage(final Product product) throws IOException {
        final ProductSubsetDef productSubsetDef = new ProductSubsetDef("subset");
        int scaleFactor = Math.max(product.getSceneRasterWidth(), product.getSceneRasterHeight()) / MAX_WIDTH;
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        productSubsetDef.setSubSampling(scaleFactor, scaleFactor);

        final String quicklookBandName = ProductUtils.findSuitableQuicklookBandName(product);
        final String expression = quicklookBandName + "==0 ? 0 : 10 * log10(abs("+quicklookBandName+"))";
        final VirtualBand virtBand = new VirtualBand("QuickLook",
                ProductData.TYPE_FLOAT32,
                product.getSceneRasterWidth(),
                product.getSceneRasterHeight(),
                expression);
        virtBand.setSynthetic(true);
        product.addBand(virtBand);

        final Product productSubset = product.createSubset(productSubsetDef, null, null);

        final BufferedImage image = ProductUtils.createColorIndexedImage(productSubset.getBand(virtBand.getName()),
                                                                         ProgressMonitor.NULL);
        productSubset.dispose();
       
        return image;
    }

    private static BufferedImage average(BufferedImage image) {

        final int rangeFactor = 2;
        final int azimuthFactor = 2;
        final int rangeAzimuth = rangeFactor * azimuthFactor;
        final Raster raster = image.getData();

        final int w = image.getWidth() / rangeFactor;
        final int h = image.getHeight() / azimuthFactor;
        int index = 0;
        final byte[] data = new byte[w*h];

        for (int ty = 0; ty < h; ++ty) {
            final int yStart = ty * azimuthFactor;
            final int yEnd = yStart + azimuthFactor;

            for (int tx = 0; tx < w; ++tx) {
                final int xStart = tx * rangeFactor;
                final int xEnd = xStart + rangeFactor;

                double meanValue = 0.0;
                for (int y = yStart; y < yEnd; ++y) {
                    for (int x = xStart; x < xEnd; ++x) {

                        meanValue += raster.getSample(x, y, 0);
                    }
                }
                meanValue /= rangeAzimuth;

                data[index++] = (byte)meanValue;
            }
        }

        return createRenderedImage(data, w, h, raster);
    }

    private static BufferedImage createRenderedImage(byte[] array, int w, int h, Raster raster) {

        // create rendered image with demension being width by height
        final SampleModel sm = RasterFactory.createBandedSampleModel(DataBuffer.TYPE_BYTE, w, h, 1);
        final ColorModel cm = PlanarImage.createColorModel(sm);
        final DataBufferByte dataBuffer = new DataBufferByte(array, array.length);
        final WritableRaster writeraster = RasterFactory.createWritableRaster(sm, dataBuffer, new Point(0,0));

        return new BufferedImage(cm, writeraster, cm.isAlphaPremultiplied(), null);
    }

    public static void createQuickLook(final int id, final Product product) {

        final File quickLookFile = getQuickLookFile(dbStorageDir, id);
        try {
            if(!dbStorageDir.exists())
                dbStorageDir.mkdirs();
            quickLookFile.createNewFile();
            final BufferedImage bufferedImage = createQuickLookImage(product);

            if(isComplex(product)) {
                ImageIO.write(average(bufferedImage), "JPG", quickLookFile);
            } else {   // detected
                ImageIO.write(bufferedImage, "JPG", quickLookFile);
            }
        } catch(Exception e) {
            System.out.println("Quicklook create data failed :"+product.getFileLocation()+"\n"+e.getMessage());
            quickLookFile.delete();
        }

       /*
        final File quickLookFile = getQuickLookFile(dbStorageDir, id);
        if(!dbStorageDir.exists())
            dbStorageDir.mkdirs();
        try {
            quickLookFile.createNewFile();
        } catch(IOException e) {
            System.out.println("Unable to create file :"+e.getMessage());
            return;
        }

        final SwingWorker worker = new SwingWorker() {
            @Override
            protected Object doInBackground() throws Exception {

                try {
                    final BufferedImage bufferedImage = createQuickLookImage(product);

                    if(isComplex(product)) {
                        ImageIO.write(average(bufferedImage), "JPG", quickLookFile);
                    } else {   // detected
                        ImageIO.write(bufferedImage, "JPG", quickLookFile);
                    }
                } catch(Exception e) {
                    System.out.println("Quicklook create data failed :"+e.getMessage());
                }

                return null;
            }

            @Override
            public void done() {
                try {
                    product.dispose();
                } catch(Exception e) {
                    //
                }
                //repoMan.fireUpdateRepositoryUI();
            }
        };
        worker.execute();    */
    }
}
