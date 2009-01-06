package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.datamodel.AbstractMetadata;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

/**
 * The product reader for ImageIO products.
 *
 */
public class ImageIOReader extends AbstractProductReader {

    ImageIOFile imgIOFile = null;
    String productType = "productType";

    private transient Map<Band, ImageIOFile.BandInfo> bandMap = new HashMap<Band, ImageIOFile.BandInfo>(3);

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
        final File inputFile = ReaderUtils.getFileFromInput(getInput());

        imgIOFile = new ImageIOFile(inputFile);

        productType = imgIOFile.reader.getFormatName();

        final Product product = new Product(imgIOFile.getName(),
                                            productType,
                                            imgIOFile.getSceneWidth(), imgIOFile.getSceneHeight());

        int bandCnt = 1;
        for(int i=0; i < imgIOFile.getNumImages(); ++i) {

            for(int b=0; b < imgIOFile.getNumBands(); ++b) {
                final Band band = new Band("band"+ bandCnt++, imgIOFile.getDataType(),
                                   imgIOFile.getSceneWidth(), imgIOFile.getSceneHeight());
                product.addBand(band);
                bandMap.put(band, new ImageIOFile.BandInfo(imgIOFile, i, b));
            }
        }

        //product.setDescription(getProductDescription());

        //addGeoCoding(product);
        addMetaData(product, inputFile);

        product.setProductReader(this);
        product.setModified(false);
        product.setFileLocation(inputFile);

        return product;
    }

    public void close() throws IOException {
        super.close();

        imgIOFile.close();
    }

    static DecodeQualification checkProductQualification(File file) {
       /* try {
            _dataDir = new ERSProductDirectory(file.getParentFile());
        } catch (Exception e) {
            return DecodeQualification.UNABLE;
        }
        if(_dataDir.isERS())
            return DecodeQualification.INTENDED;*/
        return DecodeQualification.SUITABLE;
    }

    private void addMetaData(final Product product, final File inputFile) {
        final MetadataElement root = product.getMetadataRoot();
        root.addElement(new MetadataElement(Product.ABSTRACTED_METADATA_ROOT_NAME));

        AbstractMetadata.addAbstractedMetadataHeader(root);

        final MetadataElement absRoot = root.getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, imgIOFile.getName());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, productType);

        loadExternalMetadata(absRoot, inputFile);
    }

    private static void loadExternalMetadata(final MetadataElement absRoot, final File inputFile) {
         // load metadata xml file if found
        final String inputStr = inputFile.getAbsolutePath();
        final String metadataStr = inputStr.substring(0, inputStr.lastIndexOf('.')) + ".xml";
        final File metadataFile = new File(metadataStr);
        if(metadataFile.exists())
            AbstractMetadata.Load(absRoot, metadataFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        ImageIOFile.BandInfo bandInfo = bandMap.get(destBand);

        imgIOFile.readImageIORasterBand(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight,
                              destBuffer, pm, bandInfo.imageID, bandInfo.bandSampleOffset);
    }

}
