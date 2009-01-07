
package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.datamodel.TiePointGeoCoding;
import org.esa.beam.framework.datamodel.VirtualBand;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;


public class GenericWriter extends AbstractProductWriter {

    private File _outputFile;
    private ImageOutputStream _outputStream;

    private int rasterWidth = 1012;
    private int rasterHeight = 3168;
    private int numBands = 1;
    private int dataType = ProductData.TYPE_INT16;

    private int _imageRecordLength = rasterWidth;
    private int _startPosImageRecords = 0;
    private int _imageHeaderLength = 0;

    /**
     * Construct a new instance of a product writer for the given GeoTIFF product writer plug-in.
     *
     * @param writerPlugIn the given GeoTIFF product writer plug-in, must not be <code>null</code>
     */
    public GenericWriter(final ProductWriterPlugIn writerPlugIn) {
        super(writerPlugIn);

    }

    /**
     * Writes the in-memory representation of a data product. This method was called by <code>writeProductNodes(product,
     * output)</code> of the AbstractProductWriter.
     *
     * @throws IllegalArgumentException if <code>output</code> type is not one of the supported output sources.
     * @throws java.io.IOException      if an I/O error occurs
     */
    @Override
    protected void writeProductNodesImpl() throws IOException {
        _outputFile = null;
        _outputStream = null;
       // _bandWriter = null;

        final File file;
        if (getOutput() instanceof String) {
            file = new File((String) getOutput());
        } else {
            file = (File) getOutput();
        }

        _outputStream = new FileImageOutputStream(file);

        final Product tempProduct = createWritableProduct();
       // final TiffHeader tiffHeader = new TiffHeader(new Product[]{tempProduct});
       // tiffHeader.write(_outputStream);
       // _bandWriter = new GeoTiffBandWriter(tiffHeader.getIfdAt(0), _outputStream, tempProduct);
    }

    private Product createWritableProduct() throws IOException {
        final Product sourceProduct = getSourceProduct();
        final ArrayList<String> nodeNames = new ArrayList<String>();
        for (int i = 0; i < sourceProduct.getNumBands(); i++) {
            final Band band = sourceProduct.getBandAt(i);
            if (shouldWrite(band)) {
                nodeNames.add(band.getName());
            }
        }
        final GeoCoding sourceGeoCoding = sourceProduct.getGeoCoding();
        if (sourceGeoCoding instanceof TiePointGeoCoding) {
            final TiePointGeoCoding geoCoding = (TiePointGeoCoding) sourceGeoCoding;
            nodeNames.add(geoCoding.getLatGrid().getName());
            nodeNames.add(geoCoding.getLonGrid().getName());
        }
        final ProductSubsetDef subsetDef = new ProductSubsetDef();
        subsetDef.setNodeNames(nodeNames.toArray(new String[nodeNames.size()]));
        subsetDef.setIgnoreMetadata(false);
        return sourceProduct.createSubset(subsetDef, "temp", "");
    }

    /**
     * {@inheritDoc}
     */
    public void writeBandRasterData(final Band sourceBand,
                                    final int sourceOffsetX,
                                    final int sourceOffsetY,
                                    final int sourceWidth,
                                    final int sourceHeight,
                                    final ProductData sourceBuffer,
                                    ProgressMonitor pm) throws IOException {

        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;
        final int x = sourceOffsetX;// * 2;

        sourceBuffer.writeTo(_outputStream);

      /*  pm.beginTask("Writing band...", sourceMaxY - sourceOffsetY);
        try {
            final short[] srcLine = new short[sourceWidth];
            for (int y = sourceOffsetY; y <= sourceMaxY; ++y) {
                if (pm.isCanceled()) {
                    break;
                }

                // Write source line
                synchronized (_outputStream) {
                    _outputStream.seek(_imageRecordLength * y + x);
                    sourceBuffer.writeTo(_outputStream);
                }

                pm.worked(1);
            }

        } finally {
            pm.done();
        }   */
    }

    /**
     * Deletes the physically representation of the given product from the hard disk.
     */
    public void deleteOutput() {
        if (_outputFile != null && _outputFile.isFile()) {
            _outputFile.delete();
        }
    }

    /**
     * Writes all data in memory to disk. After a flush operation, the writer can be closed safely
     *
     * @throws java.io.IOException on failure
     */
    public void flush() throws IOException {
        if (_outputStream != null) {
            _outputStream.flush();
        }
    }

    /**
     * Closes all output streams currently open.
     *
     * @throws java.io.IOException on failure
     */
    public void close() throws IOException {
        //if (_bandWriter != null) {
        //    _bandWriter.dispose();
       //     _bandWriter = null;
        //}
        if (_outputStream != null) {
            _outputStream.flush();
            _outputStream.close();
            _outputStream = null;
        }
    }

    /**
     * Returns wether the given product node is to be written.
     *
     * @param node the product node
     *
     * @return <code>true</code> if so
     */
    @Override
    public boolean shouldWrite(ProductNode node) {
        if (node instanceof VirtualBand) {
            return false;
        }
        return super.shouldWrite(node);
    }
}