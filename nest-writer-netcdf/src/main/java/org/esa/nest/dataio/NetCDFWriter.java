
package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import ucar.nc2.*;
import ucar.ma2.*;


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

    private Product createWritableProduct() throws IOException {
        final Product sourceProduct = getSourceProduct();
        final ArrayList<String> nodeNames = new ArrayList<String>();
        for (int i = 0; i < sourceProduct.getNumBands(); i++) {
            final Band band = sourceProduct.getBandAt(i);
            if (shouldWrite(band)) {
                nodeNames.add(band.getName());
            }
        }
        final GeoCoding sourceGeoCoding = sourceProduct.getGeoCoding();
        if (sourceGeoCoding instanceof TiePointGeoCoding) {
            final TiePointGeoCoding geoCoding = (TiePointGeoCoding) sourceGeoCoding;
            nodeNames.add(geoCoding.getLatGrid().getName());
            nodeNames.add(geoCoding.getLonGrid().getName());
        }
        final ProductSubsetDef subsetDef = new ProductSubsetDef();
        subsetDef.setNodeNames(nodeNames.toArray(new String[nodeNames.size()]));
        subsetDef.setIgnoreMetadata(false);
        return sourceProduct.createSubset(subsetDef, "temp", "");
    }

    private static float[] getLonData(final Product product) {
        final int size = product.getSceneRasterWidth();
        final TiePointGrid lonGrid = product.getTiePointGrid("longitude");

        return lonGrid.getPixels(0, 0, size, 1, (float[])null);
    }

    private static float[] getLatData(final Product product) {
        final int size = product.getSceneRasterHeight();
        final TiePointGrid latGrid = product.getTiePointGrid("latitude");

        return latGrid.getPixels(0, 0, 1, size, (float[])null);
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

        _outputFile = FileUtils.ensureExtension(file, NetcdfConstants.FILE_EXTENSIONS[0]);
        deleteOutput();

        final Product product = getSourceProduct();

        netCDFWriteable = NetcdfFileWriteable.createNew(_outputFile.getAbsolutePath(), true);


        netCDFWriteable.addDimension("longitude", product.getSceneRasterWidth());
        netCDFWriteable.addDimension("latitude", product.getSceneRasterHeight());

        final Group rootGroup = netCDFWriteable.getRootGroup();
        netCDFWriteable.addVariable("latitude", DataType.DOUBLE,
                new Dimension[]{rootGroup.findDimension("latitude")});
        netCDFWriteable.addVariableAttribute("latitude", "units", "degrees_north (+N/-S)");
        netCDFWriteable.addVariable("longitude", DataType.DOUBLE,
                new Dimension[]{rootGroup.findDimension("longitude")});
        netCDFWriteable.addVariableAttribute("longitude", "units", "degrees_east (+E/-W)");

        for(Band band : product.getBands()) {
            final String name = band.getName();
            netCDFWriteable.addVariable(name, DataType.DOUBLE,
                    new Dimension[]{rootGroup.findDimension("latitude"), rootGroup.findDimension("longitude")});
            netCDFWriteable.addVariableAttribute(name, "description", band.getDescription());
            netCDFWriteable.addVariableAttribute(name, "unit", band.getUnit());
        }

        addMetadata(product);

        netCDFWriteable.create();


        final Array latNcArray = Array.factory(getLatData(product));
        final Array lonNcArray = Array.factory(getLonData(product));

        try {
            netCDFWriteable.write("latitude", latNcArray);
            netCDFWriteable.write("longitude", lonNcArray);
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
        origin[0] = regionY;
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