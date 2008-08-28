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
 * The product reader for ImageIO products.
 *
 */
public class ImageIOReader extends AbstractProductReader {

    ImageIOFile imgIOFile;

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

        imgIOFile = new ImageIOFile(inputFile);

        final Product product = new Product(imgIOFile.getName(),
                                            "productType",
                                            imgIOFile.getSceneWidth(), imgIOFile.getSceneHeight());

        int bandCnt = 1;
        for(int i=0; i < imgIOFile.getNumImages(); ++i) {

            for(int b=0; b < imgIOFile.getNumBands(); ++b) {
                final Band band = new Band("band"+ bandCnt++, imgIOFile.getDataType(),
                                   imgIOFile.getSceneWidth(), imgIOFile.getSceneHeight());
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

        imgIOFile.close();
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

        imgIOFile.readImageIORasterBand(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight,
                              destBuffer, pm, bandInfo.imageID, bandInfo.bandSampleOffset);
    }

    private static class BandInfo {
        int imageID;
        int bandSampleOffset;
        public BandInfo(int id, int offset) {
            imageID = id;
            bandSampleOffset = offset;
        }
    }
}
