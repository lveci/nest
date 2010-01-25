package org.esa.beam.dataio.envi;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envi.Header.BeamProperties;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MapGeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.maptransf.*;
import org.esa.beam.util.StringUtils;
import org.esa.beam.util.TreeNode;
import org.esa.beam.util.geotiff.GeoTIFFCodes;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.text.ParseException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// @todo 2 tb/** use header offset information in file positioning
// @todo 2 tb/** evaluate file type information and react accordingly
// @todo 2 tb/** evaluate data type information and react accordingly

public class EnviProductReader extends AbstractProductReader {

    private final HashMap<Band, Long> bandStreamPositionMap = new HashMap<Band, Long>();
    private final HashMap<Band, ImageInputStream> imageInputStreamMap = new HashMap<Band, ImageInputStream>();
    private ZipFile productZip = null;

    public EnviProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    public static File createEnviImageFile(File headerFile) {
        final String hdrName = headerFile.getName();
        final String imgName = hdrName.substring(0, hdrName.indexOf('.'));
        final File parentFolder = headerFile.getParentFile();
        for(final String ext : EnviConstants.IMAGE_EXTENSIONS) {
            final File imgFile = new File(parentFolder, imgName + ext);
            if (imgFile.exists())
                return imgFile;
        }
        
        final File[] files = parentFolder.listFiles();
        for(File f : files) {
            if(f != headerFile && f.getName().startsWith(imgName)) {
                return f;
            }
        }
        return new File(parentFolder, imgName);
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        final Object inputObject = getInput();
        final File headerFile = EnviProductReaderPlugIn.getInputFile(inputObject);

        final BufferedReader headerReader = getHeaderReader(headerFile);

        final String headerFileName = headerFile.getName();
        final String productName = headerFileName.substring(0, headerFileName.indexOf('.'));

        try {
            if (getReaderPlugIn().getDecodeQualification(inputObject) == DecodeQualification.UNABLE) {
                throw new IOException("Unsupported product format.");
            }

            final Header header;
            synchronized (headerReader) {
                header = new Header(headerReader);
            }

            final Product product = new Product(productName, header.getSensorType(), header.getNumSamples(),
                    header.getNumLines());
            product.setProductReader(this);
            product.setFileLocation(headerFile);
            product.setDescription(header.getDescription());

            initGeocoding(product, header);
            initBands(product, headerFile, header);

            applyBeamProperties(product, header.getBeamProperties());

            initMetadata(product, headerFile);

            return product;
        } finally {
            if (headerReader != null) {
                headerReader.close();
            }
        }
    }

    protected void initMetadata(final Product product, final File inputFile) throws IOException {

    }

    @Override
    protected void readBandRasterDataImpl(final int sourceOffsetX, final int sourceOffsetY,
                                          final int sourceWidth, final int sourceHeight,
                                          final int sourceStepX, final int sourceStepY,
                                          final Band destBand,
                                          final int destOffsetX, final int destOffsetY,
                                          final int destWidth, final int destHeight,
                                          final ProductData destBuffer,
                                          final ProgressMonitor pm) throws IOException {

        final int sourceMinX = sourceOffsetX;
        final int sourceMinY = sourceOffsetY;
        final int sourceMaxX = sourceOffsetX + sourceWidth - 1;
        final int sourceMaxY = sourceOffsetY + sourceHeight - 1;

        final int sourceRasterWidth = destBand.getProduct().getSceneRasterWidth();
        final long bandOffset = bandStreamPositionMap.get(destBand);
        final ImageInputStream imageInputStream = imageInputStreamMap.get(destBand);

        final int elemSize = destBuffer.getElemSize();
        int destPos = 0;

        pm.beginTask("Reading band '" + destBand.getName() + "'...", sourceMaxY - sourceMinY);
        // For each scan in the data source
        try {
            for (int sourceY = sourceMinY; sourceY <= sourceMaxY; sourceY += sourceStepY) {
                if (pm.isCanceled()) {
                    break;
                }
                final int sourcePosY = sourceY * sourceRasterWidth;
                synchronized (imageInputStream) {
                    if (sourceStepX == 1) {
                        imageInputStream.seek(bandOffset + elemSize * (sourcePosY + sourceMinX));
                        destBuffer.readFrom(destPos, destWidth, imageInputStream);
                        destPos += destWidth;
                    } else {
                        for (int sourceX = sourceMinX; sourceX <= sourceMaxX; sourceX += sourceStepX) {
                            imageInputStream.seek(bandOffset + elemSize * (sourcePosY + sourceX));
                            destBuffer.readFrom(destPos, 1, imageInputStream);
                            destPos++;
                        }
                    }
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    @Override
    public void close() throws IOException {
        for(Band band : imageInputStreamMap.keySet()) {
            final ImageInputStream imageInputStream = imageInputStreamMap.get(band);
             if (imageInputStream != null) {
                imageInputStream.close();
            }
        }
        if (productZip != null) {
            productZip.close();
            productZip = null;
        }
        super.close();
    }

    public TreeNode<File> getProductComponents() {
        try {
            final File headerFile = EnviProductReaderPlugIn.getInputFile(getInput());
            File parentDir = headerFile.getParentFile();
            final TreeNode<File> root = new TreeNode<File>(parentDir.getCanonicalPath());
            root.setContent(parentDir);

            final TreeNode<File> header = new TreeNode<File>(headerFile.getName());
            header.setContent(headerFile);
            root.addChild(header);

            if (productZip == null) {
                final File imageFile = createEnviImageFile(headerFile);
                final TreeNode<File> image = new TreeNode<File>(imageFile.getName());
                image.setContent(imageFile);
                root.addChild(image);
            }

            return root;

        } catch (IOException e) {
            return null;
        }
    }

    private static ImageInputStream initializeInputStreamForBandData(File headerFile, Header header) throws IOException {
        final ImageInputStream imageInputStream;
        if (EnviProductReaderPlugIn.isCompressedFile(headerFile)) {
            imageInputStream = createImageStreamFromZip(headerFile);
        } else {
            imageInputStream = createImageStreamFromFile(headerFile);
        }
        imageInputStream.setByteOrder(header.getJavaByteOrder());
        return imageInputStream;
    }

    public static void applyBeamProperties(Product product, BeamProperties beamProperties) throws IOException {
        if (beamProperties == null) {
            return;
        }
        final String sensingStart = beamProperties.getSensingStart();
        if (sensingStart != null) {
            try {
                product.setStartTime(ProductData.UTC.parse(sensingStart));
            } catch (ParseException e) {
                final String message = e.getMessage() + " at property sensingStart in the header file.";
                throw new IOException(message, e);
            }
        }

        final String sensingStop = beamProperties.getSensingStop();
        if (sensingStop != null) {
            try {
                product.setEndTime(ProductData.UTC.parse(sensingStop));
            } catch (ParseException e) {
                final String message = e.getMessage() + " at property sensingStop in the header file.";
                throw new IOException(message, e);
            }
        }
    }

    private static ImageInputStream createImageStreamFromZip(File file) throws IOException {
        final ZipFile productZip = new ZipFile(file, ZipFile.OPEN_READ);
        try {
            final Enumeration entries = productZip.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry zipEntry = (ZipEntry) entries.nextElement();
                final String name = zipEntry.getName();
                if (name.endsWith(".img")) {
                    final InputStream zipInputStream = productZip.getInputStream(zipEntry);
                    return new FileCacheImageInputStream(zipInputStream, null);
                }
            }
        } catch (IOException e) {
            try {
                productZip.close();
            } catch (IOException e1) {
                //ignore
            }
            throw e;
        }
        return null;
    }

    private static ImageInputStream createImageStreamFromFile(final File headerFile) throws IOException {
        final File imageFile = createEnviImageFile(headerFile);

        if (!imageFile.exists()) {
            throw new FileNotFoundException("file not found: <" + imageFile + ">");
        }
        return new FileImageInputStream(imageFile);
    }

    public static void initGeocoding(final Product product, final Header header) throws IOException {
        final EnviMapInfo enviMapInfo = header.getMapInfo();
        MapTransform transform;

        // @todo tb/tb 2 this variable might be null, if so the projection is UTM.
        // The EnviMapInfo contains the parameters needed to set up the UTM projection for the product.
        final EnviProjectionInfo projectionInfo = header.getProjectionInfo();
        if (projectionInfo != null) {
                transform = EnviMapTransformFactory.create(projectionInfo.getProjectionNumber(),
                                                            projectionInfo.getParameter());
        } else if(enviMapInfo != null) {
                final MapTransformDescriptor descriptor = MapProjectionRegistry.getDescriptor(
                                                            TransverseMercatorDescriptor.TYPE_ID);
                final double[] values = descriptor.getParameterDefaultValues();

                transform = descriptor.createTransform(values);
        } else {
            return;
        }

        final MapProjection projection = new MapProjection(transform.getDescriptor().getName(), transform);
        final MapInfo mapInfo = new MapInfo(projection,
                (float) enviMapInfo.getReferencePixelX(),
                (float) enviMapInfo.getReferencePixelY(),
                (float) enviMapInfo.getEasting(),
                (float) enviMapInfo.getNorthing(),
                (float) enviMapInfo.getPixelSizeX(),
                (float) enviMapInfo.getPixelSizeY(),
                Datum.WGS_84
        );
        mapInfo.setSceneWidth(product.getSceneRasterWidth());
        mapInfo.setSceneHeight(product.getSceneRasterHeight());
        MapGeoCoding mapGeoCoding = new MapGeoCoding(mapInfo);
        product.setGeoCoding(mapGeoCoding);
    }

    /**
     * Creates a buffered reader that is opened on the *.hdr file to read the header information.
     * This method works for both compressed and uncompressed ENVI files.
     *
     * @param headerfile the input file
     * @return a reader on the header file
     * @throws IOException on disk IO failures
     */
    protected BufferedReader getHeaderReader(File headerfile) throws IOException {
        if (EnviProductReaderPlugIn.isCompressedFile(headerfile)) {
            productZip = new ZipFile(headerfile, ZipFile.OPEN_READ);
            final InputStream inStream = EnviProductReaderPlugIn.getHeaderStreamFromZip(productZip);
            return new BufferedReader(new InputStreamReader(inStream));
        } else {
            return new BufferedReader(new FileReader(headerfile));
        }
    }

    public void initBands(Product product, File headerFile, Header header) throws IOException {
        final int enviDataType = header.getDataType();
        final int dataType = DataTypeUtils.toBeam(enviDataType);
        final int sizeInBytes = DataTypeUtils.getSizeInBytes(enviDataType);
        final int bandSizeInBytes = header.getNumSamples() * header.getNumLines() * sizeInBytes;

        final int headerOffset = header.getHeaderOffset();

        final String[] bandNames = getBandNames(header);
        for (int i = 0; i < bandNames.length; i++) {
            final String originalBandName = bandNames[i];
            final String validBandName;
            final String description;
            if (Product.isValidNodeName(originalBandName)) {
                validBandName = originalBandName;
                description = "";
            } else {
                validBandName = createValidNodeName(originalBandName);
                description = "non formatted band name: " + originalBandName;
            }
            final Band band = new Band(validBandName,
                    dataType,
                    product.getSceneRasterWidth(),
                    product.getSceneRasterHeight());
            band.setDescription(description);
            product.addBand(band);

            long bandStartPosition = headerOffset + bandSizeInBytes * i;
            bandStreamPositionMap.put(band, bandStartPosition);
            imageInputStreamMap.put(band, initializeInputStreamForBandData(headerFile, header));
        }
    }

    protected static String[] getBandNames(final Header header) {
        final String[] bandNames = header.getBandNames();
        // there must be at least 1 bandname because in DIMAP-Files are no bandnames given.
        if (bandNames == null || bandNames.length == 0) {
            return new String[]{"Band"};
        } else {
            return bandNames;
        }
    }

    private static String createValidNodeName(final String originalBandName) {
        String name = StringUtils.createValidName(originalBandName, null, '_');
        while (name.startsWith("_")) {
            name = name.substring(1);
        }
        while (name.endsWith("_")) {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }
}