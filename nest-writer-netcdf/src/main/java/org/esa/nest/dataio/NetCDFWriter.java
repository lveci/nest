
package org.esa.nest.dataio;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.dataio.AbstractProductWriter;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.datamodel.TiePointGeoCoding;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Dimension;
import ucar.ma2.*;


public class NetCDFWriter extends AbstractProductWriter {

    private File _outputFile;
    private NetcdfFileWriteable netCDFWriteable;
    private Dimension latDim, lonDim;

    /**
     * Construct a new instance of a product writer for the given product writer plug-in.
     *
     * @param writerPlugIn the given product writer plug-in, must not be <code>null</code>
     */
    public NetCDFWriter(final ProductWriterPlugIn writerPlugIn) {
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
        _outputFile = null;

        final File file;
        if (getOutput() instanceof String) {
            file = new File((String) getOutput());
        } else {
            file = (File) getOutput();
        }

        _outputFile = FileUtils.ensureExtension(file, NetcdfConstants.FILE_EXTENSIONS[0]);
        deleteOutput();

        final Product tempProduct = createWritableProduct();

        netCDFWriteable = NetcdfFileWriteable.createNew(_outputFile.getAbsolutePath(), true);

           // define dimensions, including unlimited
        latDim = netCDFWriteable.addDimension("lat", tempProduct.getSceneRasterWidth());
        lonDim = netCDFWriteable.addDimension("lon", tempProduct.getSceneRasterHeight());
        Dimension timeDim = netCDFWriteable.addUnlimitedDimension("time");

        // define Variables
        Dimension[] dim2 = new Dimension[3];
        dim2[0] = timeDim;
        dim2[1] = lonDim;
        dim2[2] = latDim;

        netCDFWriteable.addVariable("lat", DataType.FLOAT, new Dimension[] {latDim});
        netCDFWriteable.addVariableAttribute("lat", "units", "degrees_north");

        netCDFWriteable.addVariable("lon", DataType.FLOAT, new Dimension[] {lonDim});
        netCDFWriteable.addVariableAttribute("lon", "units", "degrees_east");

        for(Band band : tempProduct.getBands()) {
            final String name = band.getName();
            netCDFWriteable.addVariable(name, DataType.DOUBLE, dim2);
            netCDFWriteable.addVariableAttribute(name, "description", band.getDescription());
            netCDFWriteable.addVariableAttribute(name, "unit", band.getUnit());
        }

        netCDFWriteable.create();


        try {
            double[] lt = new double[latDim.getLength()];
            Arrays.fill(lt, 123);
            double[] lg = new double[lonDim.getLength()];
            Arrays.fill(lg, 123);

            double[] d = new double[latDim.getLength() * lonDim.getLength()];
            Arrays.fill(d, 123);

            netCDFWriteable.write("lat", Array.factory(lt));
            netCDFWriteable.write("lon", Array.factory(lg));

            netCDFWriteable.write("Amplitude", Array.factory(d));
          
        } catch(InvalidRangeException e) {

        }
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

        //try {
            //netCDFWriteable.write("lat", Array.factory(new float[] {41, 40, 39}));
            //netCDFWriteable.write("lon", Array.factory(new float[] {-109, -107, -105, -103}));

            //netCDFWriteable.write(sourceBand.getName(), Array.factory(regionData.getElems()));


        //} catch(InvalidRangeException e) {

        //}
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