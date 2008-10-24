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
 * Reader for ImageIO File
 *
 */
public class ImageIOFile {

    private int sceneWidth;
    private int sceneHeight;
    private int dataType;
    private int numImages;
    private int numBands;
    private String name;

    ImageOutputStream stream;
    ImageReader reader;
    ImageReadParam param;
    final IIOMetadata iioMetadata;

    /**
     *
     */
    public ImageIOFile(final File inputFile) throws IOException {

        stream = ImageIO.createImageOutputStream(inputFile);
        if(stream == null)
            throw new IOException("Unable to open " + inputFile.toString());

        Iterator iter = ImageIO.getImageReaders(stream);
        reader = (ImageReader) iter.next();
        param = reader.getDefaultReadParam();
        reader.setInput(stream);

        iioMetadata = reader.getImageMetadata(0);

        name = inputFile.getName();
        numImages = reader.getNumImages(true);
        numBands = 3;

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
    }

    public void close() throws IOException {
        stream.close();
        reader.dispose();
    }

    public String getName() {
        return name;
    }

    public int getSceneWidth() {
        return sceneWidth;
    }

    public int getSceneHeight() {
        return sceneHeight;
    }

    public int getDataType() {
        return dataType;
    }

    public int getNumImages() {
        return numImages;
    }

    public int getNumBands() {
        return numBands;
    }

    public void readImageIORasterBand(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                            ProductData destBuffer, ProgressMonitor pm,
                                            int imageID, int sampleOffset)
                                                                                                throws IOException {
        final Rectangle srcRect = new Rectangle(sourceOffsetX, sourceOffsetY, sourceWidth, sourceHeight);

        param.setSourceRegion(srcRect);
        param.setSourceBands(new int[]{imageID});
        param.setDestinationBands(new int[]{0});

        final RenderedImage image = reader.read(0, param);
        final java.awt.image.Raster data = image.getData();

        final int size = destBuffer.getNumElems();
        final int elemSize = data.getNumDataElements();

        final int[] b = new int[size * elemSize];
        data.getPixels(0, 0, sourceWidth, sourceHeight, b);

        //if(elemSize == 1) {
        //    System.arraycopy(b, 0, destBuffer.getElems(), 0, destWidth);
        //} else {
            final int length = b.length;
            for(int i=0, j=sampleOffset; i < size && j < length; ++i, j+=elemSize) {
                destBuffer.setElemIntAt(i, b[j]);
            }
        //}
    }

    public static class BandInfo {
        public int imageID;
        public int bandSampleOffset;
        public ImageIOFile img;
        
        public BandInfo(ImageIOFile imgFile, int id, int offset) {
            img = imgFile;
            imageID = id;
            bandSampleOffset = offset;
        }
    }
}