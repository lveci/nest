/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.imageio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.nest.gpf.ReaderUtils;
import org.esa.nest.datamodel.AbstractMetadata;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;


public class ImageIOWriter extends AbstractProductWriter {

    private ImageOutputStream _outputStream;
    private ImageWriter writer;
    private final String format;

    /**
     * Construct a new instance of a product writer for the given product writer plug-in.
     *
     * @param writerPlugIn the given product writer plug-in, must not be <code>null</code>
     * @param format
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

        final File file = ReaderUtils.getFileFromInput(getOutput());

        Iterator<ImageWriter> writerList = ImageIO.getImageWritersByFormatName(format);
        writer = writerList.next();

        _outputStream = ImageIO.createImageOutputStream(file);
        writer.setOutput(_outputStream);

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(getSourceProduct());
        AbstractMetadata.saveExternalMetadata(getSourceProduct(), absRoot, file);

        setIncrementalMode(false);
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
        try {
            final ImageWriteParam param = writer.getDefaultWriteParam();
            
            param.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            param.setDestinationOffset(new Point(sourceOffsetX, sourceOffsetY));
            
            param.setSourceRegion(new Rectangle(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight));
            param.setTiling(sourceWidth, sourceHeight, sourceOffsetX, sourceOffsetY);
            writer.write(null, new IIOImage(sourceBand.getSourceImage(), null, null), param);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static RenderedImage createRenderedImage(final double[] array, final int w, final int h) {

        // create rendered image with demension being width by height
        final SampleModel sampleModel = RasterFactory.createBandedSampleModel(DataBuffer.TYPE_DOUBLE, w, h, 1);
        final ColorModel colourModel = PlanarImage.createColorModel(sampleModel);
        final DataBufferDouble dataBuffer = new DataBufferDouble(array, array.length);
        final WritableRaster raster = RasterFactory.createWritableRaster(sampleModel, dataBuffer, new Point(0,0));

        return new BufferedImage(colourModel, raster, false, new Hashtable());
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
        if(writer != null) {
            writer.dispose();
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
        return !(node instanceof VirtualBand) && super.shouldWrite(node);
    }
}