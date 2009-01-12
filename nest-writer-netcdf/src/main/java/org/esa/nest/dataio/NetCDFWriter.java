
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

import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Dimension;
import ucar.nc2.Group;
import ucar.nc2.Variable;
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

        //final Product tempProduct = createWritableProduct();
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

        netCDFWriteable.create();

        //double[][] tData = {{1, 2, 3, 4},
        //        {2, 4, 6, 8},
        //        {3, 6, 9, 12}};

        Array latNcArray = Array.factory(getLatData(product));
        Array lonNcArray = Array.factory(getLonData(product));

        //Array gridArray = Array.factory(tData);
        try {
            netCDFWriteable.write("latitude", latNcArray);
            netCDFWriteable.write("longitude", lonNcArray);
            //netCDFWriteable.write("data", gridArray);
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

        final Variable variable = netCDFWriteable.findVariable(sourceBand.getName());
        final int rank = variable.getRank();
        final int[] origin = new int[rank];
        for (int i = 0; i < rank; i++) {
            origin[i] = 0;
        }
        origin[rank - 1] = regionX;

        try {

            final double[][] data = new double[regionWidth][1];
            for(int x=0; x < regionWidth; ++x) {
                data[x][0] = regionData.getElemDoubleAt(x);
            }
            Array dataArray = Array.factory(data);

            //for (int y = 0; y < regionHeight; y++) {
                origin[rank - 2] = regionY;// + y;
               
                netCDFWriteable.write(sourceBand.getName(), origin, dataArray);
                pm.worked(1);
            //}

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
}