package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ImageInfo;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.awt.*;
import java.awt.image.RenderedImage;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;

/**
 * The product reader for ERS products.
 *
 */
public class ImageIOReader extends AbstractProductReader {

    private int sceneWidth;
    private int sceneHeight;
    private int dataType;

    ImageOutputStream stream;
    ImageReader reader;
    ImageReadParam param;

    private transient Map<Band, BandInfo> bandMap = new HashMap<Band, BandInfo>(3);

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public ImageIOReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    /**
     * Returns a <code>File</code> if the given input is a <code>String</code> or <code>File</code>,
     * otherwise it returns null;
     *
     * @param input an input object of unknown type
     *
     * @return a <code>File</code> or <code>null</code> it the input can not be resolved to a <code>File</code>.
     */
    public static File getFileFromInput(final Object input) {
        if (input instanceof String) {
            return new File((String) input);
        } else if (input instanceof File) {
            return (File) input;
        }
        return null;
    }

    /**
     * Provides an implementation of the <code>readProductNodes</code> interface method. Clients implementing this
     * method can be sure that the input object and eventually the subset information has already been set.
     * <p/>
     * <p>This method is called as a last step in the <code>readProductNodes(input, subsetInfo)</code> method.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    protected Product readProductNodesImpl() throws IOException {
        final ProductReaderPlugIn readerPlugIn = getReaderPlugIn();
        final Object input = getInput();
        if (readerPlugIn.getDecodeQualification(input) == DecodeQualification.UNABLE) {
            throw new IOException("Unsupported product format."); 
        }
        final File inputFile = getFileFromInput(getInput());

        stream = ImageIO.createImageOutputStream(inputFile);
        if(stream == null)
            throw new IOException("Unable to open " + inputFile.toString());
        
        Iterator iter = ImageIO.getImageReaders(stream);
        reader = (ImageReader) iter.next();
        param = reader.getDefaultReadParam();
        reader.setInput(stream);

        final IIOMetadata iioMetadata = reader.getImageMetadata(0);

        int numImages = reader.getNumImages(true);
        int numBands = 3;

        sceneWidth = reader.getWidth(0);
        sceneHeight = reader.getHeight(0);

        dataType = ProductData.TYPE_INT32;
        ImageTypeSpecifier its = reader.getRawImageType(0);
        if(its != null) {
            numBands = reader.getRawImageType(0).getNumBands();
            int type = its.getBufferedImageType();

            if(type > dataType)
                dataType = type;
        }                                                           

        final Product product = new Product(inputFile.getName(),
                                            "productType",
                                            sceneWidth, sceneHeight);

        int bandCnt = 1;
        for(int i=0; i < numImages; ++i) {

            for(int b=0; b < numBands; ++b) {
                final Band band = new Band("band"+ bandCnt++, dataType,
                                   sceneWidth, sceneHeight);
                product.addBand(band);
                bandMap.put(band, new BandInfo(i, b));
            }
        }

        //product.setDescription(getProductDescription());

        //addGeoCoding(product);
        //addMetaData(product);

        product.setProductReader(this);
        product.setModified(false);

        return product;
    }

    public void close() throws IOException {
        super.close();

        stream.close();
        reader.dispose();
    }

    DecodeQualification checkProductQualification(File file) {
       /* try {
            _dataDir = new ERSProductDirectory(file.getParentFile());
        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
        if(_dataDir.isERS())
            return DecodeQualification.INTENDED;*/
        return DecodeQualification.SUITABLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        BandInfo bandInfo = bandMap.get(destBand);

        Rectangle srcRect = new Rectangle(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight);
        Rectangle dstRect = new Rectangle(0, 0, sourceWidth, sourceHeight);

        param.setSourceRegion(srcRect);
        param.setSourceBands(new int[]{bandInfo.imageID});
        param.setDestinationBands(new int[]{0});

        final RenderedImage image = reader.read(0, param);
        java.awt.image.Raster data = image.getData();

        //IIOImage iioImage = reader.readAll(bandInfo.imageID, param);
        //java.awt.image.Raster data = iioImage.getRenderedImage().getData();

        int size = destBuffer.getNumElems();
        int elemSize = data.getNumDataElements();

        int[] b = new int[size * elemSize];
        data.getPixels(0, 0, sourceWidth, sourceHeight, b);

        //if(elemSize == 1) {
        //    System.arraycopy(b, 0, destBuffer.getElems(), 0, destWidth);
        //} else {
            int length = b.length;
            for(int i=0, j=bandInfo.bandSampleOffset; i < size && j < length; ++i, j+=elemSize) {
                destBuffer.setElemIntAt(i, b[j]);
            }
        //}
    }

    private static class BandInfo {
        //IIOImage iioImage;
        int imageID;
        int bandSampleOffset;
        public BandInfo(int id, int offset) {
            imageID = id;
            bandSampleOffset = offset;
        }
    }
}
