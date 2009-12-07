package org.esa.nest.dataio.cosmo;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.IllegalFileFormatException;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.maptransf.Datum;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.logging.BeamLogManager;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.datamodel.AbstractMetadataIO;
import org.esa.nest.dataio.*;
import org.esa.nest.util.XMLSupport;
import org.jdom.Element;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * The product reader for CosmoSkymed products.
 *
 */
public class CosmoSkymedReader extends AbstractProductReader {

    private NetcdfFile netcdfFile = null;
    private Product product = null;
    private NcVariableMap variableMap = null;
    private boolean yFlipped = false;
    private final ProductReaderPlugIn readerPlugIn;

    private final static String timeFormat = "yyyy-MM-dd HH:mm:ss";

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    public CosmoSkymedReader(final ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
        this.readerPlugIn = readerPlugIn;
    }

    private void initReader() {
        product = null;
        netcdfFile = null;
        variableMap = null;
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
        removeQuickLooks(variableListMap);

        final NcRasterDim rasterDim = NetCDFUtils.getBestRasterDim(variableListMap);
        final Variable[] rasterVariables = NetCDFUtils.getRasterVariables(variableListMap, rasterDim);
        final Variable[] tiePointGridVariables = NetCDFUtils.getTiePointGridVariables(variableListMap, rasterVariables);

        this.netcdfFile = netcdfFile;
        variableMap = new NcVariableMap(rasterVariables);
        yFlipped = false;

        final NcAttributeMap globalAttributes = NcAttributeMap.create(this.netcdfFile);

        product = new Product(inputFile.getName(),
                               NetCDFUtils.getProductType(globalAttributes, readerPlugIn.getFormatNames()[0]),
                               rasterDim.getDimX().getLength(),
                               rasterDim.getDimY().getLength(),
                               this);
        product.setFileLocation(inputFile);
        product.setDescription(NetCDFUtils.getProductDescription(globalAttributes));
        product.setStartTime(NetCDFUtils.getSceneRasterStartTime(globalAttributes));
        product.setEndTime(NetCDFUtils.getSceneRasterStopTime(globalAttributes));

        addMetadataToProduct();
        addBandsToProduct(rasterVariables);
        addTiePointGridsToProduct(tiePointGridVariables);
        addGeoCodingToProduct(rasterDim);
        product.setModified(false);

        return product;
    }

    @Override
    public void close() throws IOException {
        if (product != null) {
            product = null;
            variableMap.clear();
            variableMap = null;
            netcdfFile.close();
            netcdfFile = null;
        }
        super.close();
    }

    private static void removeQuickLooks(Map<NcRasterDim, List<Variable>> variableListMap) {
        final String[] excludeList = { "qlk" };
        final NcRasterDim[] keys = variableListMap.keySet().toArray(new NcRasterDim[variableListMap.keySet().size()]);
        final ArrayList<NcRasterDim> removeList = new ArrayList<NcRasterDim>();

        for (final NcRasterDim rasterDim : keys) {
            final List<Variable> varList = variableListMap.get(rasterDim);
            boolean found = false;
            for(Variable v : varList) {
                if(found) break;

                final String vName = v.getName().toLowerCase();
                for(String str : excludeList) {
                    if(vName.contains(str)) {
                        removeList.add(rasterDim);
                        found = true;
                        break;
                    }
                }
            }
        }
        for(NcRasterDim key : removeList) {
            variableListMap.remove(key);
        }
    }

    private void addMetadataToProduct() {

        NetCDFUtils.addAttributes(product.getMetadataRoot(), NetcdfConstants.GLOBAL_ATTRIBUTES_NAME,
                                  netcdfFile.getGlobalAttributes());

        addDeliveryNote(product);

        for (final Variable variable : variableMap.getAll()) {
            NetCDFUtils.addAttributes(product.getMetadataRoot(), variable.getName(),
                                  variable.getAttributes());
        }

        //final Group rootGroup = netcdfFile.getRootGroup();
        //NetCDFUtils.addGroups(product.getMetadataRoot(), rootGroup);

        addAbstractedMetadataHeader(product, product.getMetadataRoot());
    }

    private void addDeliveryNote(final Product product) {
        try {
            final File folder = product.getFileLocation().getParentFile();
            File dnFile = null;
            for(File f : folder.listFiles()) {
                final String name = f.getName().toLowerCase();
                if(name.startsWith("dfdn") && name.endsWith("xml")) {
                    dnFile = f;
                    break;
                }
            }
            if(dnFile != null) {
                final org.jdom.Document xmlDoc = XMLSupport.LoadXML(dnFile.getAbsolutePath());
                final Element rootElement = xmlDoc.getRootElement();

                AbstractMetadataIO.AddXMLMetadata(rootElement, product.getMetadataRoot());
            }
        } catch(IOException e) {
            ReaderUtils.showErrorMsg("Unable to read Delivery Note for "+product.getName());
        }
    }

    private void addAbstractedMetadataHeader(Product product, MetadataElement root) {

        final MetadataElement absRoot = AbstractMetadata.addAbstractedMetadataHeader(root);

        final String defStr = AbstractMetadata.NO_METADATA_STRING;
        final int defInt = AbstractMetadata.NO_METADATA;

        final MetadataElement globalElem = root.getElement(NetcdfConstants.GLOBAL_ATTRIBUTES_NAME);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT, globalElem.getAttributeString("Product Filename", defStr));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PRODUCT_TYPE, globalElem.getAttributeString("Product Type", defStr));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SPH_DESCRIPTOR,
                globalElem.getAttributeString("Acquisition Mode", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.MISSION, globalElem.getAttributeString("Satellite Id", "CSK"));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PROC_TIME,
                ReaderUtils.getTime(globalElem, "Product Generation UTC", timeFormat));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ProcessingSystemIdentifier,
                globalElem.getAttributeString("Processing Centre", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.ABS_ORBIT, globalElem.getAttributeInt("Orbit Number", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.PASS, globalElem.getAttributeString("Orbit Direction", defStr));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.SAMPLE_TYPE, getSampleType(globalElem));

        final ProductData.UTC startTime = ReaderUtils.getTime(globalElem, "Scene Sensing Start UTC", timeFormat);
        final ProductData.UTC stopTime = ReaderUtils.getTime(globalElem, "Scene Sensing Stop UTC", timeFormat);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.first_line_time, startTime);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.last_line_time, stopTime);
        product.setStartTime(startTime);
        product.setEndTime(stopTime);

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_output_lines,
                product.getSceneRasterHeight());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.num_samples_per_line,
                product.getSceneRasterWidth());
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.TOT_SIZE, ReaderUtils.getTotalSize(product));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.radar_frequency,
                globalElem.getAttributeDouble("Radar Frequency", defInt) / 1000000.0);
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.line_time_interval,
                ReaderUtils.getLineTimeInterval(startTime, stopTime, product.getSceneRasterHeight()));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.algorithm,
                globalElem.getAttributeString("Focusing Algorithm ID", defStr));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.geo_ref_system,
                globalElem.getAttributeString("Ellipsoid Designator", defStr));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_looks,
                globalElem.getAttributeDouble("Range Processing Number of Looks", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_looks,
                globalElem.getAttributeDouble("Azimuth Processing Number of Looks", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.range_spacing,
                globalElem.getAttributeDouble("Ground Range Geometric Resolution", defInt));
        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.azimuth_spacing,
                globalElem.getAttributeDouble("Azimuth Geometric Resolution", defInt));

        AbstractMetadata.setAttribute(absRoot, AbstractMetadata.srgr_flag, 1);
    }

    private static String getSampleType(final MetadataElement globalElem) {
        if(globalElem.getAttributeInt("Samples per Pixel", 0) > 1)
            return "COMPLEX";
        return "DETECTED";
    }

    private void addBandsToProduct(final Variable[] variables) {
        for (Variable variable : variables) {
            final int height = variable.getDimension(0).getLength();
            final int width = variable.getDimension(1).getLength();
            final Band band = NetCDFUtils.createBand(variable, width, height);
            int cnt = 1;
            final String origName = band.getName();
            while(product.getBand(band.getName()) != null) {
                band.setName(origName + cnt);
                ++cnt;
            }
            product.addBand(band);
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
                        product.getSceneRasterWidth(), product.getSceneRasterHeight());

            product.addTiePointGrid(tpg);
        }

        addGeocodingFromMetadata();
    }

    private void addGeocodingFromMetadata() {
        final MetadataElement root = product.getMetadataRoot();
        final String bandName = product.getBandAt(0).getName();
        MetadataElement bandElem = null;
        for(MetadataElement elem : root.getElements()) {
            if(elem.getName().equalsIgnoreCase(bandName)) {
                bandElem = elem;
                break;
            }
        }

        if(bandElem != null) {
            try {
                String str = bandElem.getAttributeString("Top Left Geodetic Coordinates");
                final float latUL = Float.parseFloat(str.substring(0, str.indexOf(',')));
                final float lonUL = Float.parseFloat(str.substring(str.indexOf(',')+1, str.lastIndexOf(',')));
                str = bandElem.getAttributeString("Top Right Geodetic Coordinates");
                final float latUR = Float.parseFloat(str.substring(0, str.indexOf(',')));
                final float lonUR = Float.parseFloat(str.substring(str.indexOf(',')+1, str.lastIndexOf(',')));
                str = bandElem.getAttributeString("Bottom Left Geodetic Coordinates");
                final float latLL = Float.parseFloat(str.substring(0, str.indexOf(',')));
                final float lonLL = Float.parseFloat(str.substring(str.indexOf(',')+1, str.lastIndexOf(',')));
                str = bandElem.getAttributeString("Bottom Right Geodetic Coordinates");
                final float latLR = Float.parseFloat(str.substring(0, str.indexOf(',')));
                final float lonLR = Float.parseFloat(str.substring(str.indexOf(',')+1, str.lastIndexOf(',')));

                final float[] latCorners = new float[]{latUL, latUR, latLL, latLR};
                final float[] lonCorners = new float[]{lonUL, lonUR, lonLL, lonLR};

                ReaderUtils.addGeoCoding(product, latCorners, lonCorners);
            } catch(Exception e) {
                System.out.println(e.getMessage());
                // continue
            }
        }
    }

    private void addGeoCodingToProduct(final NcRasterDim rasterDim) throws IOException {
        if (product.getGeoCoding() == null) {
            NetCDFReader.setTiePointGeoCoding(product);
        }
        if (product.getGeoCoding() == null) {
            NetCDFReader.setPixelGeoCoding(product);
        }
        if (product.getGeoCoding() == null) {
            yFlipped = NetCDFReader.setMapGeoCoding(rasterDim, product, netcdfFile, yFlipped);
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

        final int sceneHeight = product.getSceneRasterHeight();
        final int y0 = yFlipped ? (sceneHeight - 1) - sourceOffsetY : sourceOffsetY;

        final Variable variable = variableMap.get(destBand.getName());
        final int rank = variable.getRank();
        final int[] origin = new int[rank];
        final int[] shape = new int[rank];
        for (int i = 0; i < rank; i++) {
            shape[i] = 1;
            origin[i] = 0;
        }
        shape[0] = 1;
        shape[1] = destWidth;
        origin[1] = sourceOffsetX;

        pm.beginTask("Reading data from band " + destBand.getName(), destHeight);
        try {
            for (int y = 0; y < destHeight; y++) {
                origin[0] = yFlipped ? y0 - y : y0 + y;
                final Array array;
                synchronized (netcdfFile) {
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