package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.logging.BeamLogManager;
import org.esa.beam.util.Guardian;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import ucar.nc2.Variable;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Group;
import ucar.nc2.Attribute;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;

/**
 * The product reader for NetCDF products.
 *
 */
public class NetCDFReader extends AbstractProductReader {

    private NetcdfFile _netcdfFile = null;
    private Product _product = null;
    private NcVariableMap _variableMap = null;
    private boolean _yFlipped = false;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public NetCDFReader(final ProductReaderPlugIn readerPlugIn) {
       super(readerPlugIn);
    }

    private void initReader() {
        _product = null;
        _netcdfFile = null;
        _variableMap = null;
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
        final File inputFile = ReaderUtils.getFileFromInput(getInput());
        initReader();

        final NetcdfFile netcdfFile = NetcdfFile.open(inputFile.getPath());
        if (netcdfFile == null) {
            close();
            throw new IllegalFileFormatException(inputFile.getName() +
                    " Could not be interpretted by the NetCDF reader.");
        }

        Map<NcRasterDim, List<Variable>> variableListMap = NetCDFUtils.getVariableListMap(netcdfFile.getRootGroup());
        if (variableListMap.isEmpty()) {
            close();
            throw new IllegalFileFormatException("No netCDF variables found which could\n" +
                    "be interpreted as remote sensing bands.");  /*I18N*/
        }
        final NcRasterDim rasterDim = NetCDFUtils.getBestRasterDim(variableListMap);
        final Variable[] rasterVariables = NetCDFUtils.getRasterVariables(variableListMap, rasterDim);

        _netcdfFile = netcdfFile;
        _variableMap = new NcVariableMap(rasterVariables);
        _yFlipped = false;

        final NcAttributeMap globalAttributes = NcAttributeMap.create(_netcdfFile);

        _product = new Product(inputFile.getName(),
                               NetCDFUtils.getProductType(globalAttributes),
                               rasterDim.getDimX().getLength(),
                               rasterDim.getDimY().getLength(),
                               this);
        _product.setFileLocation(inputFile);
        _product.setDescription(NetCDFUtils.getProductDescription(globalAttributes));
        _product.setStartTime(NetCDFUtils.getSceneRasterStartTime(globalAttributes));
        _product.setEndTime(NetCDFUtils.getSceneRasterStopTime(globalAttributes));

        addMetadataToProduct();
        addBandsToProduct(rasterVariables);
        addGeoCodingToProduct(rasterDim);
        _product.setModified(false);

        return _product;
    }

    public void close() throws IOException {
        if (_product != null) {
            _product = null;
            _variableMap.clear();
            _variableMap = null;
            _netcdfFile.close();
            _netcdfFile = null;
        }
        super.close();
    }

    private void addMetadataToProduct() {

        final Group rootGroup = _netcdfFile.getRootGroup();
        NetCDFUtils.addGroups(_product.getMetadataRoot(), rootGroup);
        
        final List<Attribute> globalAttribList = _netcdfFile.getGlobalAttributes();
        for(Attribute at : globalAttribList) {
            NetCDFUtils.createMetadataAttributes(_product.getMetadataRoot(), at);
        }
    }

    private void addBandsToProduct(final Variable[] variables) {
        for (Variable variable : variables) {
            final int rank = variable.getRank();
            final int width = variable.getDimension(rank - 1).getLength();
            final int height = variable.getDimension(rank - 2).getLength();
            final Band band = NetCDFUtils.createBand(variable, width, height);

            _product.addBand(band);
        }
    }


    private void addGeoCodingToProduct(final NcRasterDim rasterDim) throws IOException {
        setMapGeoCoding(rasterDim);
        if (_product.getGeoCoding() == null) {
            setPixelGeoCoding(rasterDim);
        }
    }

    private void setMapGeoCoding(final NcRasterDim rasterDim) {
        final NcVariableMap varMap = NcVariableMap.create(_netcdfFile);
        // CF convention
        Variable lonVar = varMap.get(NetcdfConstants.LON_VAR_NAME);
        if (lonVar == null) {
            // COARDS convention
            lonVar = varMap.get(NetcdfConstants.LONGITUDE_VAR_NAME);
        }
        // CF convention
        Variable latVar = varMap.get(NetcdfConstants.LAT_VAR_NAME);
        if (latVar == null) {
            // COARDS convention
            latVar = varMap.get(NetcdfConstants.LATITUDE_VAR_NAME);
        }
        if (lonVar != null && latVar != null && rasterDim.fitsTo(lonVar, latVar)) {
            try {
                final NetCDFUtils.MapInfoX mapInfoX = NetCDFUtils.createMapInfoX(lonVar, latVar,
                                                                                 _product.getSceneRasterWidth(),
                                                                                 _product.getSceneRasterHeight());
                if (mapInfoX != null) {
                    _yFlipped = mapInfoX.isYFlipped();
                    _product.setGeoCoding(new MapGeoCoding(mapInfoX.getMapInfo()));
                }
            } catch (IOException e) {
                BeamLogManager.getSystemLogger().warning("Failed to create NetCDF geo-coding");
            }
        }
    }

    private void setPixelGeoCoding(final NcRasterDim rasterDim) throws
                                                                IOException {
        Band lonBand = _product.getBand(NetcdfConstants.LON_VAR_NAME);
        if (lonBand == null) {
            lonBand = _product.getBand(NetcdfConstants.LONGITUDE_VAR_NAME);
        }
        Band latBand = _product.getBand(NetcdfConstants.LAT_VAR_NAME);
        if (latBand == null) {
            latBand = _product.getBand(NetcdfConstants.LATITUDE_VAR_NAME);
        }
        if (latBand != null && lonBand != null) {
            _product.setGeoCoding(new PixelGeoCoding(latBand,
                                                     lonBand,
                                                     latBand.getValidPixelExpression(),
                                                     5, ProgressMonitor.NULL));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {

        Guardian.assertTrue("sourceStepX == 1 && sourceStepY == 1", sourceStepX == 1 && sourceStepY == 1);
        Guardian.assertTrue("sourceWidth == destWidth", sourceWidth == destWidth);
        Guardian.assertTrue("sourceHeight == destHeight", sourceHeight == destHeight);

        final int sceneHeight = _product.getSceneRasterHeight();
        final int y0 = _yFlipped ? (sceneHeight - 1) - sourceOffsetY : sourceOffsetY;

        final Variable variable = _variableMap.get(destBand.getName());
        final int rank = variable.getRank();
        final int[] origin = new int[rank];
        final int[] shape = new int[rank];
        for (int i = 0; i < rank; i++) {
            shape[i] = 1;
            origin[i] = 0;
        }
        shape[rank - 2] = 1;
        shape[rank - 1] = destWidth;
        origin[rank - 1] = sourceOffsetX;

        pm.beginTask("Reading data from band " + destBand.getName(), destHeight);
        try {
            for (int y = 0; y < destHeight; y++) {
                origin[rank - 2] = _yFlipped ? y0 - y : y0 + y;
                final Array array;
                synchronized (_netcdfFile) {
                    array = variable.read(origin, shape);
                }
                final Object storage = array.getStorage();
                System.arraycopy(storage, 0, destBuffer.getElems(), y * destWidth, destWidth);
                pm.worked(1);
                if (pm.isCanceled()) {
                    new IOException("Process terminated by user."); /*I18N*/
                }
            }
        } catch (InvalidRangeException e) {
            final IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        } finally {
            pm.done();
        }
    }

}