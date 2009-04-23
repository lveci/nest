
package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.io.FileUtils;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.Group;
import ucar.nc2.NetcdfFileWriteable;

import java.io.File;
import java.io.IOException;


public class NetCDFWriter extends AbstractProductWriter {

    private File _outputFile = null;
    private NetcdfFileWriteable netCDFWriteable = null;

    /**
     * Construct a new instance of a product writer for the given product writer plug-in.
     *
     * @param writerPlugIn the given product writer plug-in, must not be <code>null</code>
     */
    public NetCDFWriter(final ProductWriterPlugIn writerPlugIn) {
        super(writerPlugIn);
    }

    private static float[] getLonData(final Product product, final String lonGridName) {
        final int size = product.getSceneRasterWidth();
        final TiePointGrid lonGrid = product.getTiePointGrid(lonGridName);

        return lonGrid.getPixels(0, 0, size, 1, (float[])null);
    }

    private static float[] getLatData(final Product product, final String latGridName) {
        final int size = product.getSceneRasterHeight();
        final TiePointGrid latGrid = product.getTiePointGrid(latGridName);

        return latGrid.getPixels(0, 0, 1, size, (float[])null);
    }

    private static float[][] getTiePointGridData(final TiePointGrid tpg) {
        final float[][] data = new float[tpg.getRasterHeight()][tpg.getRasterWidth()];
        final ProductData productData = tpg.getData();
        for(int y=0; y < tpg.getRasterHeight(); ++y) {
            final int stride = y * tpg.getRasterWidth();
            for(int x=0; x < tpg.getRasterWidth(); ++x) {
                data[y][x] = productData.getElemFloatAt(stride + x);
            }
        }
        return data;
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
        _outputFile = null;

        final File file;
        if (getOutput() instanceof String) {
            file = new File((String) getOutput());
        } else {
            file = (File) getOutput();
        }

        _outputFile = FileUtils.ensureExtension(file, NetcdfConstants.NETCDF_FORMAT_FILE_EXTENSIONS[0]);
        deleteOutput();

        final Product product = getSourceProduct();

        netCDFWriteable = NetcdfFileWriteable.createNew(_outputFile.getAbsolutePath(), true);


        netCDFWriteable.addDimension(NetcdfConstants.LON_VAR_NAMES[0], product.getSceneRasterWidth());
        netCDFWriteable.addDimension(NetcdfConstants.LAT_VAR_NAMES[0], product.getSceneRasterHeight());

        final Group rootGroup = netCDFWriteable.getRootGroup();
        netCDFWriteable.addVariable(NetcdfConstants.LAT_VAR_NAMES[0], DataType.FLOAT,
                new Dimension[]{rootGroup.findDimension(NetcdfConstants.LAT_VAR_NAMES[0])});
        netCDFWriteable.addVariableAttribute(NetcdfConstants.LAT_VAR_NAMES[0], "units", "degrees_north (+N/-S)");
        netCDFWriteable.addVariable(NetcdfConstants.LON_VAR_NAMES[0], DataType.FLOAT,
                new Dimension[]{rootGroup.findDimension(NetcdfConstants.LON_VAR_NAMES[0])});
        netCDFWriteable.addVariableAttribute(NetcdfConstants.LON_VAR_NAMES[0], "units", "degrees_east (+E/-W)");

        for(Band band : product.getBands()) {
            final String name = band.getName();
            netCDFWriteable.addVariable(name, DataType.DOUBLE,
                    new Dimension[]{rootGroup.findDimension(NetcdfConstants.LAT_VAR_NAMES[0]),
                                    rootGroup.findDimension(NetcdfConstants.LON_VAR_NAMES[0])});
            if(band.getDescription() != null)
                netCDFWriteable.addVariableAttribute(name, "description", band.getDescription());
            netCDFWriteable.addVariableAttribute(name, "unit", band.getUnit());
        }

        for(TiePointGrid tpg : product.getTiePointGrids()) {
            final String name = tpg.getName();
            netCDFWriteable.addDimension(name+'x', tpg.getRasterWidth());
            netCDFWriteable.addDimension(name+'y', tpg.getRasterHeight());
            netCDFWriteable.addVariable(name, DataType.FLOAT,
                    new Dimension[]{rootGroup.findDimension(name+'y'), rootGroup.findDimension(name+'x')});
            if(tpg.getDescription() != null)
                netCDFWriteable.addVariableAttribute(name, "description", tpg.getDescription());
            if(tpg.getUnit() != null)
                netCDFWriteable.addVariableAttribute(name, "unit", tpg.getUnit());
        }

        addMetadata(product);

        netCDFWriteable.create();


        final GeoCoding sourceGeoCoding = product.getGeoCoding();
        String latGridName = "latitude";
        String lonGridName = "longitude";
        if (sourceGeoCoding instanceof TiePointGeoCoding) {
            final TiePointGeoCoding geoCoding = (TiePointGeoCoding) sourceGeoCoding;
            latGridName = geoCoding.getLatGrid().getName();
            lonGridName = geoCoding.getLonGrid().getName();
        }

        final Array latNcArray = Array.factory(getLatData(product, latGridName));
        final Array lonNcArray = Array.factory(getLonData(product, lonGridName));

        try {
            netCDFWriteable.write(NetcdfConstants.LAT_VAR_NAMES[0], latNcArray);
            netCDFWriteable.write(NetcdfConstants.LON_VAR_NAMES[0], lonNcArray);

            for(TiePointGrid tpg : product.getTiePointGrids()) {
                final Array tpgArray = Array.factory(getTiePointGridData(tpg));
                netCDFWriteable.write(tpg.getName(), tpgArray);
            }
        } catch (InvalidRangeException rangeE) {
            rangeE.printStackTrace();
            throw new RuntimeException(rangeE);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void writeBandRasterData(final Band sourceBand,
                                    final int regionX,
                                    final int regionY,
                                    final int regionWidth,
                                    final int regionHeight,
                                    final ProductData regionData,
                                    ProgressMonitor pm) throws IOException {

        final int[] origin = new int[2];
        origin[1] = regionX;
        origin[0] = (sourceBand.getRasterHeight() - 1) - regionY;
        try {

            final double[][] data = new double[1][regionWidth];
            for(int x=0; x < regionWidth; ++x) {
                data[0][x] = regionData.getElemDoubleAt(x);
            }
               
            netCDFWriteable.write(sourceBand.getName(), origin, Array.factory(data));
            pm.worked(1);

        } catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Deletes the physically representation of the given product from the hard disk.
     */
    public void deleteOutput() {
        if (_outputFile != null && _outputFile.isFile()) {
            _outputFile.delete();
        }
    }

    /**
     * Closes all output streams currently open.
     *
     * @throws java.io.IOException on failure
     */
    public void close() throws IOException {
        netCDFWriteable.close();
    }

    /**
     * Writes all data in memory to disk. After a flush operation, the writer can be closed safely
     *
     * @throws IOException on failure
     */
    public void flush() throws IOException {
        if (netCDFWriteable == null) {
            return;
        }
        netCDFWriteable.flush();
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

    private void addMetadata(final Product product) {

        final MetadataElement rootElem = product.getMetadataRoot();
        final Group rootGroup = netCDFWriteable.getRootGroup();

        addElements(rootElem, rootGroup);
        addAttributes(rootElem, rootGroup);
    }

    private void addElements(final MetadataElement parentElem, final Group parentGroup) {
        final MetadataElement[] elemList = parentElem.getElements();
        for(MetadataElement child : elemList) {
            final Group newGroup = new Group(netCDFWriteable, parentGroup, child.getName());
            addAttributes(child, newGroup);
            // recurse
            addElements(child, newGroup);

            netCDFWriteable.addGroup(parentGroup, newGroup);
        }
    }

    private void addAttributes(final MetadataElement elem, final Group newGroup) {
        final MetadataAttribute[] attributes = elem.getAttributes();
            for(MetadataAttribute attrib : attributes) {
                final int dataType = attrib.getDataType();
                if(dataType == ProductData.TYPE_FLOAT32 || dataType == ProductData.TYPE_FLOAT64) {
                    newGroup.addAttribute(new Attribute(attrib.getName(), elem.getAttributeDouble(attrib.getName(), 0)));
                } else if(dataType > ProductData.TYPE_INT8 && dataType < ProductData.TYPE_FLOAT32) {
                    newGroup.addAttribute(new Attribute(attrib.getName(), elem.getAttributeInt(attrib.getName(), 0)));
                } else {
                    newGroup.addAttribute(new Attribute(attrib.getName(), elem.getAttributeString(attrib.getName(), " ")));
                }
            }
    }
}