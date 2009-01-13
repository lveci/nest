
package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.IIOImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.awt.*;


public class ImageIOWriter extends AbstractProductWriter {

    private ImageOutputStream _outputStream;
    private ImageIOFile imgIOFile = null;
    private ImageWriter writer;
    private final String format;

    /**
     * Construct a new instance of a product writer for the given product writer plug-in.
     *
     * @param writerPlugIn the given product writer plug-in, must not be <code>null</code>
     */
    public ImageIOWriter(final ProductWriterPlugIn writerPlugIn, final String format) {
        super(writerPlugIn);

        this.format = format;
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
        _outputStream = null;

        final File file;
        if (getOutput() instanceof String) {
            file = new File((String) getOutput());
        } else {
            file = (File) getOutput();
        }

        Iterator<ImageWriter> writerList = ImageIO.getImageWritersByFormatName(format);
        writer = writerList.next();

        _outputStream = ImageIO.createImageOutputStream(file);
        writer.setOutput(_outputStream);

        final MetadataElement absRoot = getSourceProduct().getMetadataRoot().getElement(Product.ABSTRACTED_METADATA_ROOT_NAME);
        AbstractMetadata.saveExternalMetadata(getSourceProduct(), absRoot, file);
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

        //sourceBuffer.writeTo(_outputStream);

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setSourceRegion(new Rectangle(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight));
        writer.write(null, new IIOImage(sourceBand.getSourceImage(), null, null), param);
    }

    /**
     * Deletes the physically representation of the given product from the hard disk.
     */
    public void deleteOutput() {

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