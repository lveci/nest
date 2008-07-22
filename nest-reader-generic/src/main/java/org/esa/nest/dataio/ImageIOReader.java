package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.awt.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageReadParam;
import javax.imageio.IIOImage;
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

        ImageOutputStream stream = ImageIO.createImageOutputStream(inputFile);
        Iterator iter = ImageIO.getImageReaders(stream);
        ImageReader reader = (ImageReader) iter.next();
        ImageReadParam param = reader.getDefaultReadParam();
        reader.setInput(stream);

        final IIOMetadata iioMetadata = reader.getImageMetadata(0);

        int numImages = reader.getNumImages(true);
        
        sceneWidth = reader.getWidth(0);
        sceneHeight = reader.getHeight(0);
        int type = reader.getRawImageType(0).getBufferedImageType();
        dataType = ProductData.TYPE_INT32;
        if(type > dataType)
            dataType = type;

        int numBands = reader.getRawImageType(0).getNumBands();

        final Product product = new Product(inputFile.getName(),
                                            "productType",
                                            sceneWidth, sceneHeight);

        for(int i=0; i < numImages; ++i) {

            IIOImage iioImage = reader.readAll(i, param);

            for(int b=0; b < numBands; ++b) {
                final Band band = new Band("band"+ (i+b), dataType,
                                   sceneWidth, sceneHeight);
                product.addBand(band);
                bandMap.put(band, new BandInfo(iioImage, b));
            }
        }



        //product.setDescription(getProductDescription());

        //addGeoCoding(product);
        //addMetaData(product);

        product.setProductReader(this);
        product.setModified(false);

        stream.close();
        reader.dispose();

        return product;
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
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        BandInfo bandInfo = bandMap.get(destBand);

        java.awt.image.Raster data = bandInfo.iioImage.getRenderedImage().getData(
                new Rectangle(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight));

        int size = destBuffer.getNumElems();
        int elemSize = data.getNumDataElements();

        int[] b = new int[size * elemSize];
        data.getPixels(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight, b);

        int length = b.length;
        for(int i=0, j=bandInfo.bandSampleOffset; i < size && j < length; ++i, j+=elemSize) {
            destBuffer.setElemIntAt(i, b[j]);
        }

    }

    private static class BandInfo {
        IIOImage iioImage;
        int bandSampleOffset;
        public BandInfo(IIOImage image, int offset) {
            iioImage = image;
            bandSampleOffset = offset;
        }
    }
}
