package org.jdoris.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.dataio.dimap.DimapProductReader;
import org.esa.beam.dataio.dimap.EnviHeader;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.Debug;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.io.FileUtils;
import org.esa.nest.dataio.FileImageOutputStreamExtImpl;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.Unit;
import org.jdoris.core.Orbit;
import org.jdoris.core.SLCImage;
import org.jdoris.core.Window;
import org.jdoris.core.unwrapping.snaphu.SnaphuConfigFile;
import org.jdoris.core.unwrapping.snaphu.SnaphuParameters;

import javax.imageio.stream.ImageOutputStream;
import java.io.*;
import java.lang.Exception;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * The SNAPHU product writer based on ENVI products writer.
 */
public class SnaphuWriter extends AbstractProductWriter {

    // TODO: inherit EnviProductWriter of BEAM, and implement/override only new code for SNAPHU export

    private File _outputDir;
    private File _outputFile;
    private Map _bandOutputStreams;
    private boolean _incremental = true;

    private static final String SNAPHU_CONFIG_FILE = "snaphu.conf";
    private SnaphuConfigFile snaphuConfigFile = new SnaphuConfigFile();
    private final ByteOrder byteOrder = ByteOrder.nativeOrder();

    /**
     * Construct a new instance of a product writer for the given ENVI product writer plug-in.
     *
     * @param writerPlugIn the given ENVI product writer plug-in, must not be <code>null</code>
     */
    public SnaphuWriter(ProductWriterPlugIn writerPlugIn) {
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
        final Object output = getOutput();

        File outputFile = null;
        if (output instanceof String) {
            outputFile = new File((String) output);
        } else if (output instanceof File) {
            outputFile = (File) output;
        }
        Debug.assertNotNull(outputFile); // super.writeProductNodes should have checked this already
        initDirs(outputFile);

        ensureNamingConvention();
        Product sourceProduct = getSourceProduct();

        // set up product writer
        sourceProduct.setProductWriter(this);
        deleteRemovedNodes();

        // dump snaphu config file
	    createSnaphuConfFile();

    }

    /**
     * Initializes all the internal file and directory elements from the given output file. This method only must be
     * called if the product writer should write the given data to raw data files without calling of writeProductNodes.
     * This may be at the time when a dimap product was opened and the data shold be continously changed in the same
     * product file without an previous call to the saveProductNodes to this product writer.
     *
     * @param outputFile the dimap header file location.
     */
    void initDirs(final File outputFile) {
        final String name = FileUtils.getFilenameWithoutExtension(outputFile);
        _outputDir = outputFile.getParentFile();
        if (_outputDir == null) {
            _outputDir = new File(".");
        }
        _outputDir = new File(_outputDir, name);
        _outputDir.mkdirs();
        _outputFile = new File(_outputDir, outputFile.getName());
    }

    private void ensureNamingConvention() {
        if (_outputFile != null) {
            getSourceProduct().setName(FileUtils.getFilenameWithoutExtension(_outputFile));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeBandRasterData(Band sourceBand,
                                    int sourceOffsetX, int sourceOffsetY,
                                    int sourceWidth, int sourceHeight,
                                    ProductData sourceBuffer,
                                    ProgressMonitor pm) throws IOException {
        Guardian.assertNotNull("sourceBand", sourceBand);
        Guardian.assertNotNull("sourceBuffer", sourceBuffer);
        checkBufferSize(sourceWidth, sourceHeight, sourceBuffer);
        final int sourceBandWidth = sourceBand.getSceneRasterWidth();
        final int sourceBandHeight = sourceBand.getSceneRasterHeight();
        checkSourceRegionInsideBandRegion(sourceWidth, sourceBandWidth, sourceHeight, sourceBandHeight, sourceOffsetX,
                sourceOffsetY);
        final ImageOutputStream outputStream = getOrCreateImageOutputStream(sourceBand);
        long outputPos = sourceOffsetY * sourceBandWidth + sourceOffsetX;
        pm.beginTask("Writing band '" + sourceBand.getName() + "'...", 1);//sourceHeight);
        try {
            final long max = sourceHeight * sourceWidth;
            final int size = sourceBuffer.getElemSize();
            for (int sourcePos = 0; sourcePos < max; sourcePos += sourceWidth) {
                sourceBuffer.writeTo(sourcePos, sourceWidth, size, outputStream, outputPos);
                outputPos += sourceBandWidth;
            }
            pm.worked(1);
        } finally {
            pm.done();
        }
    }

    /**
     * Deletes the physically representation of the product from the hard disk.
     */
    public void deleteOutput() throws IOException {
        flush();
        close();
        if (_outputFile != null && _outputFile.exists() && _outputFile.isFile()) {
            _outputFile.delete();
        }
    }

    private static void checkSourceRegionInsideBandRegion(int sourceWidth, final int sourceBandWidth, int sourceHeight,
                                                          final int sourceBandHeight, int sourceOffsetX,
                                                          int sourceOffsetY) {
        Guardian.assertWithinRange("sourceWidth", sourceWidth, 1, sourceBandWidth);
        Guardian.assertWithinRange("sourceHeight", sourceHeight, 1, sourceBandHeight);
        Guardian.assertWithinRange("sourceOffsetX", sourceOffsetX, 0, sourceBandWidth - sourceWidth);
        Guardian.assertWithinRange("sourceOffsetY", sourceOffsetY, 0, sourceBandHeight - sourceHeight);
    }

    private static void checkBufferSize(int sourceWidth, int sourceHeight, ProductData sourceBuffer) {
        final int expectedBufferSize = sourceWidth * sourceHeight;
        final int actualBufferSize = sourceBuffer.getNumElems();
        Guardian.assertEquals("sourceWidth * sourceHeight", actualBufferSize, expectedBufferSize);  /*I18N*/
    }

    /**
     * Writes all data in memory to disk. After a flush operation, the writer can be closed safely
     *
     * @throws java.io.IOException on failure
     */
    public void flush() throws IOException {
        if (_bandOutputStreams == null) {
            return;
        }
        for (Object o : _bandOutputStreams.values()) {
            ((ImageOutputStream) o).flush();
        }

        // at the very end also save SnaphuConfig file
        try {
            createSnaphuConfFile();
        } catch (Exception ignored) {
        }

    }

    /**
     * Closes all output streams currently open.
     *
     * @throws java.io.IOException on failure
     */
    public void close() throws IOException {
        if (_bandOutputStreams == null) {
            return;
        }
        for (Object o : _bandOutputStreams.values()) {
            ((ImageOutputStream) o).close();
        }
        _bandOutputStreams.clear();
        _bandOutputStreams = null;
    }

    /**
     * Returns the data output stream associated with the given <code>Band</code>. If no stream exists, one is created
     * and fed into the hash map
     */
    private ImageOutputStream getOrCreateImageOutputStream(Band band) throws IOException {
        ImageOutputStream outputStream = getImageOutputStream(band);
        if (outputStream == null) {
            outputStream = createImageOutputStream(band);
            if (_bandOutputStreams == null) {
                _bandOutputStreams = new HashMap();
            }

            outputStream.setByteOrder(byteOrder);
            _bandOutputStreams.put(band, outputStream);
        }
        return outputStream;
    }

    private ImageOutputStream getImageOutputStream(Band band) {
        if (_bandOutputStreams != null) {
            return (ImageOutputStream) _bandOutputStreams.get(band);
        }
        return null;
    }

    /**
     * Returns a file associated with the given <code>Band</code>. The method ensures that the file exists and have the
     * right size. Also ensures a recreate if the file not exists or the file have a different file size. A new envi
     * header file was written every call.
     */
    private File getValidImageFile(Band band) throws IOException {
        writeEnviHeader(band); // always (re-)write ENVI header
        final File file = getImageFile(band);
        if (file.exists()) {
            if (file.length() != getImageFileSize(band)) {
                createPhysicalImageFile(band, file);
            }
        } else {
            createPhysicalImageFile(band, file);
        }
        return file;
    }

    private static void createPhysicalImageFile(Band band, File file) throws IOException {
        createPhysicalFile(file, getImageFileSize(band));
    }

    private void writeEnviHeader(Band band) throws IOException {
        EnviHeader.createPhysicalFile(getEnviHeaderFile(band),
                band,
                band.getRasterWidth(),
                band.getRasterHeight());
    }

    private ImageOutputStream createImageOutputStream(Band band) throws IOException {
        return new FileImageOutputStreamExtImpl(getValidImageFile(band));
    }

    private static long getImageFileSize(RasterDataNode band) {
        return (long) ProductData.getElemSize(band.getDataType()) *
                (long) band.getRasterWidth() *
                (long) band.getRasterHeight();
    }

    private File getEnviHeaderFile(Band band) {
        return new File(_outputDir, createEnviHeaderFilename(band));
    }

    private static String createEnviHeaderFilename(Band band) {
        return band.getName() + EnviHeader.FILE_EXTENSION;
    }

    private File getImageFile(Band band) {
        return new File(_outputDir, createImageFilename(band));
    }

    protected String createImageFilename(Band band) {
        return band.getName() + DimapProductConstants.IMAGE_FILE_EXTENSION;
    }

    private static void createPhysicalFile(File file, long fileSize) throws IOException {
        final File parentDir = file.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
        final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.setLength(fileSize);
        randomAccessFile.close();
    }

    @Override
    public boolean shouldWrite(ProductNode node) {
        if (node instanceof VirtualBand) {
            return false;
        }
        if (node instanceof FilterBand) {
            return false;
        }
        if (node.isModified()) {
            return true;
        }
        if (!isIncrementalMode()) {
            return true;
        }
        if (!(node instanceof Band)) {
            return true;
        }
        final File imageFile = getImageFile((Band) node);
        return !(imageFile != null && imageFile.exists());
    }

    /**
     * Enables resp. disables incremental writing of this product writer. By default, a reader should enable progress
     * listening.
     *
     * @param enabled enables or disables progress listening.
     */
    @Override
    public void setIncrementalMode(boolean enabled) {
        _incremental = enabled;
    }

    /**
     * Returns whether this product writer writes only modified product nodes.
     *
     * @return <code>true</code> if so
     */
    @Override
    public boolean isIncrementalMode() {
        return _incremental;
    }

    private void deleteRemovedNodes() throws IOException {
        final Product product = getSourceProduct();
        final ProductReader productReader = product.getProductReader();
        if (productReader instanceof DimapProductReader) {
            final ProductNode[] removedNodes = product.getRemovedChildNodes();
            if (removedNodes.length > 0) {
                productReader.close();
                for (ProductNode removedNode : removedNodes) {
                    removedNode.removeFromFile(this);
                }
            }
        }
    }

    @Override
    public void removeBand(Band band) {
        if (band != null) {
            final String headerFilename = createEnviHeaderFilename(band);
            final String imageFilename = createImageFilename(band);
            File[] files = null;
            if (_outputDir != null && _outputDir.exists()) {
                files = _outputDir.listFiles();
            }
            if (files == null) {
                return;
            }
            String name;
            for (File file : files) {
                name = file.getName();
                if (file.isFile() && (name.equals(headerFilename) || name.equals(imageFilename))) {
                    file.delete();
                }
            }
        }
    }

    protected File getOutputDir() {
        return _outputDir;
    }

    private void createSnaphuConfFile() throws IOException {

        final Product sourceProduct = getSourceProduct();

        // prepare snaphu config file
        final MetadataElement masterRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        final MetadataElement[] slaveRootS = sourceProduct.getMetadataRoot().getElement(AbstractMetadata.SLAVE_METADATA_ROOT).getElements();
        final MetadataElement slaveRoot = slaveRootS[0];

        final SLCImage masterMetadata = new SLCImage(masterRoot);
        final SLCImage slaveMetadata = new SLCImage(slaveRoot);

        Orbit masterOrbit = null;
        Orbit slaveOrbit = null;
        try {
            masterOrbit = new Orbit(masterRoot, 3);
            slaveOrbit = new Orbit(slaveRoot, 3);
        } catch (Exception ignored) {
        }

        String cohName = null;
        String phaseName = null;
        for (Band band : sourceProduct.getBands()) {
            if (band.getUnit().contains(Unit.COHERENCE)) {
                cohName = band.getName();
            }
            if (band.getUnit().contains(Unit.PHASE)) {
                phaseName = band.getName();
            }
        }

        SnaphuParameters parameters = new SnaphuParameters();
        String temp;
        temp = masterRoot.getAttributeString(AbstractMetadata.temp_1, "DEFO");
        parameters.setUnwrapMode(temp);

        temp = masterRoot.getAttributeString(AbstractMetadata.temp_2, "MST");
        parameters.setSnaphuInit(temp);

        parameters.setLogFileName("snaphu.log");
        parameters.setPhaseFileName(phaseName + ".img");
        parameters.setCoherenceFileName(cohName + ".img");
        parameters.setVerbosityFlag("true");

        parameters.setOutFileName("Unw_" + phaseName + ".img");

        Window dataWindow = new Window(masterMetadata.getCurrentWindow());

        /// initiate snaphuconfig
        try {
            snaphuConfigFile = new SnaphuConfigFile(masterMetadata, slaveMetadata, masterOrbit, slaveOrbit, dataWindow, parameters);
            snaphuConfigFile.buildConfFile();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // write snaphu.conf file to the target directory
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(_outputDir + "/" + SNAPHU_CONFIG_FILE));
            out.write(snaphuConfigFile.getConfigFileBuffer().toString());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
