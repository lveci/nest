package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import com.sun.media.imageioimpl.plugins.tiff.TIFFImageReader;
import org.esa.beam.framework.datamodel.ColorPaletteDef;
import org.esa.beam.framework.datamodel.ImageInfo;
import org.esa.beam.framework.datamodel.IndexCoding;
import org.esa.beam.framework.datamodel.ProductData;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Reader for ImageIO File
 *
 */
public class ImageIOFile {

    private final int sceneWidth;
    private final int sceneHeight;
    private int dataType;
    private final int numImages;
    private int numBands;
    private final String name;
    private ImageInfo imageInfo = null;
    private IndexCoding indexCoding = null;
    private boolean isIndexed = false;

    private ImageInputStream stream = null;
    private ImageReader reader = null;

    public static ImageReader getIIOReader(final File inputFile) throws IOException {
        final ImageInputStream stream = ImageIO.createImageInputStream(inputFile);
        if(stream == null)
            throw new IOException("Unable to open " + inputFile.toString());

        final Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(stream);
        final ImageReader reader = imageReaders.next();

        if(reader == null)
            throw new IOException("Unable to open " + inputFile.toString());
        return reader;
    }

    public static ImageReader getTiffIIOReader(final File inputFile) throws IOException {
        final ImageInputStream stream = ImageIO.createImageInputStream(inputFile);
        if(stream == null)
            throw new IOException("Unable to open " + inputFile.toString());

        ImageReader reader = null;
        final Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(stream);
        while(imageReaders.hasNext()) {
            final ImageReader iioReader = imageReaders.next();
            if(iioReader instanceof TIFFImageReader) {
                reader = iioReader;
                break;
            }
        }
        if(reader == null)
            throw new IOException("Unable to open " + inputFile.toString());
        return reader;
    }

    public ImageReader getReader() {
        return reader;
    }

    public ImageIOFile(final File inputFile, final ImageReader iioReader) throws IOException {

        stream = ImageIO.createImageInputStream(inputFile);
        if(stream == null)
            throw new IOException("Unable to open " + inputFile.toString());

        reader = iioReader;
        reader.setInput(stream);

        //IIOMetadata iioMetadata = reader.getImageMetadata(0);

        name = inputFile.getName();
        numImages = reader.getNumImages(true);
        numBands = 3;

        sceneWidth = reader.getWidth(0);
        sceneHeight = reader.getHeight(0);

        dataType = ProductData.TYPE_INT32;
        final ImageTypeSpecifier its = reader.getRawImageType(0); 
        if(its != null) {
            numBands = reader.getRawImageType(0).getNumBands();
            dataType = bufferImageTypeToProductType(its.getBufferedImageType());

            if(its.getBufferedImageType() == BufferedImage.TYPE_BYTE_INDEXED) {
                isIndexed = true;
                createIndexedImageInfo(its.getColorModel());
            }
        }
    }

    private static int bufferImageTypeToProductType(int biType) {
        switch(biType) {
            case BufferedImage.TYPE_CUSTOM:
            case BufferedImage.TYPE_INT_RGB:
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
            case BufferedImage.TYPE_INT_BGR:
                return ProductData.TYPE_INT32;
            case BufferedImage.TYPE_3BYTE_BGR:
            case BufferedImage.TYPE_4BYTE_ABGR:
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                return ProductData.TYPE_INT16;
            case BufferedImage.TYPE_USHORT_565_RGB:
            case BufferedImage.TYPE_USHORT_555_RGB:
            case BufferedImage.TYPE_USHORT_GRAY:
                return ProductData.TYPE_UINT16;
            case BufferedImage.TYPE_BYTE_GRAY:
            case BufferedImage.TYPE_BYTE_BINARY:
            case BufferedImage.TYPE_BYTE_INDEXED:
                return ProductData.TYPE_INT8;
        }
        return ProductData.TYPE_UNDEFINED;
    }

    final void createIndexedImageInfo(ColorModel colorModel) {
        final IndexColorModel indexColorModel = (IndexColorModel)colorModel;
        indexCoding = new IndexCoding("color_map");
        final int colorCount = indexColorModel.getMapSize();
        final ColorPaletteDef.Point[] points = new ColorPaletteDef.Point[colorCount];
        for (int j = 0; j < colorCount; j++) {
            final String name = "I%3d";
            indexCoding.addIndex(String.format(name, j), j, "");
            points[j] = new ColorPaletteDef.Point(j, new Color(indexColorModel.getRGB(j)), name);
        }

        imageInfo = new ImageInfo(new ColorPaletteDef(points, points.length));
    }

    public boolean isIndexed() {
        return isIndexed;
    }

    public IndexCoding getIndexCoding() {
        return indexCoding;
    }

    public ImageInfo getImageInfo() {
        return imageInfo;
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
        public final int imageID;
        public final int bandSampleOffset;
        public final ImageIOFile img;
        
        public BandInfo(ImageIOFile imgFile, int id, int offset) {
            img = imgFile;
            imageID = id;
            bandSampleOffset = offset;
        }
    }
}