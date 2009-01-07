package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.ProductData;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.awt.*;
import java.awt.image.RenderedImage;
import java.awt.image.Raster;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
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

    private ImageOutputStream stream;
    ImageReader reader;
    
    /**
     *
     */
    public ImageIOFile(final File inputFile) throws IOException {

        stream = ImageIO.createImageOutputStream(inputFile);
        if(stream == null)
            throw new IOException("Unable to open " + inputFile.toString());

        final Iterator iter = ImageIO.getImageReaders(stream);
        reader = (ImageReader) iter.next();
        reader.setInput(stream);

        IIOMetadata iioMetadata = reader.getImageMetadata(0);

        name = inputFile.getName();
        numImages = reader.getNumImages(true);
        numBands = 3;

        sceneWidth = reader.getWidth(0);
        sceneHeight = reader.getHeight(0);

        dataType = ProductData.TYPE_INT32;
        final ImageTypeSpecifier its = reader.getRawImageType(0);
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

    public synchronized void readImageIORasterBand(final int sourceOffsetX, final int sourceOffsetY,
                                                   final int sourceWidth, final int sourceHeight,
                                                   final int sourceStepX, final int sourceStepY,
                                                   final ProductData destBuffer,
                                                   final int destOffsetX, final int destOffsetY,
                                                   final int destWidth, final int destHeight,
                                                   final ProgressMonitor pm, final int imageID) throws IOException {

        final ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(sourceStepX, sourceStepY,
                                   sourceOffsetX % sourceStepX,
                                   sourceOffsetY % sourceStepY);

        final RenderedImage image = reader.readAsRenderedImage(0, param);
        final Raster data = image.getData(new Rectangle(destOffsetX, destOffsetY, destWidth, destHeight));

        final double[] dArray = new double[destWidth * destHeight];
        final DataBuffer dataBuffer = data.getDataBuffer();
        final SampleModel sampleModel = data.getSampleModel();
        sampleModel.getSamples(0, 0, destWidth, destHeight, imageID, dArray, dataBuffer);
        pm.worked(1);

        for (int i = 0; i < dArray.length; i++) {
            destBuffer.setElemDoubleAt(i, dArray[i]);
        }
        pm.worked(1);
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