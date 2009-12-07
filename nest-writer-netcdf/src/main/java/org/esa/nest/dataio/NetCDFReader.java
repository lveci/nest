package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.logging.BeamLogManager;
import org.esa.nest.datamodel.AbstractMetadata;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The product reader for NetCDF products.
 *
 */
public class NetCDFReader extends AbstractProductReader {

    private NetcdfFile _netcdfFile = null;
    private Product _product = null;
    private NcVariableMap _variableMap = null;
    private boolean _yFlipped = false;
    private final ProductReaderPlugIn _readerPlugIn;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public NetCDFReader(final ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
        this._readerPlugIn = readerPlugIn;
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
                    " Could not be interpretted by the reader.");
        }

        final Map<NcRasterDim, List<Variable>> variableListMap = NetCDFUtils.getVariableListMap(netcdfFile.getRootGroup());
        if (variableListMap.isEmpty()) {
            close();
            throw new IllegalFileFormatException("No netCDF variables found which could\n" +
                    "be interpreted as remote sensing bands.");  /*I18N*/
        }
        final NcRasterDim rasterDim = NetCDFUtils.getBestRasterDim(variableListMap);
        final Variable[] rasterVariables = NetCDFUtils.getRasterVariables(variableListMap, rasterDim);
        final Variable[] tiePointGridVariables = NetCDFUtils.getTiePointGridVariables(variableListMap, rasterVariables);

        _netcdfFile = netcdfFile;
        _variableMap = new NcVariableMap(rasterVariables);
        _yFlipped = false;

        final NcAttributeMap globalAttributes = NcAttributeMap.create(_netcdfFile);

        _product = new Product(inputFile.getName(),
                               NetCDFUtils.getProductType(globalAttributes, _readerPlugIn.getFormatNames()[0]),
                               rasterDim.getDimX().getLength(),
                               rasterDim.getDimY().getLength(),
                               this);
        _product.setFileLocation(inputFile);
        _product.setDescription(NetCDFUtils.getProductDescription(globalAttributes));
        _product.setStartTime(NetCDFUtils.getSceneRasterStartTime(globalAttributes));
        _product.setEndTime(NetCDFUtils.getSceneRasterStopTime(globalAttributes));

        addMetadataToProduct();
        addBandsToProduct(rasterVariables);
        addTiePointGridsToProduct(tiePointGridVariables);
        addGeoCodingToProduct(rasterDim);
        _product.setModified(false);

        return _product;
    }

    @Override
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

        AbstractMetadata.addAbstractedMetadataHeader(_product.getMetadataRoot());
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

    private void addTiePointGridsToProduct(final Variable[] variables) throws IOException {
        for (Variable variable : variables) {
            final int rank = variable.getRank();
            final int gridWidth = variable.getDimension(rank - 1).getLength();
            int gridHeight = variable.getDimension(rank - 2).getLength();
            if(rank >= 3 && gridHeight <= 1)
                gridHeight = variable.getDimension(rank - 3).getLength();
            final TiePointGrid tpg = NetCDFUtils.createTiePointGrid(variable, gridWidth, gridHeight,
                        _product.getSceneRasterWidth(), _product.getSceneRasterHeight());

            _product.addTiePointGrid(tpg);
        }
    }

    private void addGeoCodingToProduct(final NcRasterDim rasterDim) throws IOException {
        setTiePointGeoCoding(_product);
        if (_product.getGeoCoding() == null) {
            setPixelGeoCoding(_product);
        }
        if (_product.getGeoCoding() == null) {
            _yFlipped = setMapGeoCoding(rasterDim, _product, _netcdfFile, _yFlipped);
        }
    }

    public static boolean setMapGeoCoding(final NcRasterDim rasterDim, final Product product,
                                       NetcdfFile netcdfFile, boolean yFlipped) {
        final NcVariableMap varMap = NcVariableMap.create(netcdfFile);

        Variable lonVar=null, latVar=null;
        for(String lonStr : NetcdfConstants.LON_VAR_NAMES) {
            lonVar = varMap.get(lonStr);
            if(lonVar != null)
                break;
        }
        for(String latStr : NetcdfConstants.LAT_VAR_NAMES) {
            latVar = varMap.get(latStr);
            if(latVar != null)
                break;
        }
        if (lonVar != null && latVar != null && rasterDim.fitsTo(lonVar, latVar)) {
            try {
                final NetCDFUtils.MapInfoX mapInfoX = NetCDFUtils.createMapInfoX(lonVar, latVar,
                                                                                 product.getSceneRasterWidth(),
                                                                                 product.getSceneRasterHeight());
                if (mapInfoX != null) {
                    yFlipped = mapInfoX.isYFlipped();
                    product.setGeoCoding(new MapGeoCoding(mapInfoX.getMapInfo()));
                }
            } catch (IOException e) {
                BeamLogManager.getSystemLogger().warning("Failed to create NetCDF geo-coding");
            }
        }
        return yFlipped;
    }

    public static void setTiePointGeoCoding(final Product product) {
        TiePointGrid lonGrid=null, latGrid=null;
        for(String lonStr : NetcdfConstants.LON_VAR_NAMES) {
            lonGrid = product.getTiePointGrid(lonStr);
            if(lonGrid != null)
                break;
        }
        for(String latStr : NetcdfConstants.LAT_VAR_NAMES) {
            latGrid = product.getTiePointGrid(latStr);
            if(latGrid != null)
                break;
        }
        if (latGrid != null && lonGrid != null) {       
            final TiePointGeoCoding tpGeoCoding = new TiePointGeoCoding(latGrid, lonGrid, Datum.WGS_84);
            product.setGeoCoding(tpGeoCoding);
        }
    }

    public static void setPixelGeoCoding(final Product product) throws IOException {
        Band lonBand=null, latBand=null;
        for(String lonStr : NetcdfConstants.LON_VAR_NAMES) {
            lonBand = product.getBand(lonStr);
            if(lonBand != null)
                break;
        }
        for(String latStr : NetcdfConstants.LAT_VAR_NAMES) {
            latBand = product.getBand(latStr);
            if(latBand != null)
                break;
        }
        if (latBand != null && lonBand != null) {
            product.setGeoCoding(new PixelGeoCoding(latBand, lonBand,
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
                System.arraycopy(array.getStorage(), 0, destBuffer.getElems(), y * destWidth, destWidth);
                pm.worked(1);
                if (pm.isCanceled()) {
                    throw new IOException("Process terminated by user."); /*I18N*/
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