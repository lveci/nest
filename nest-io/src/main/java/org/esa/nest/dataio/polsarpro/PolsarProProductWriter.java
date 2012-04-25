
package org.esa.nest.dataio.polsarpro;

import org.esa.beam.dataio.envi.EnviProductWriter;
import org.esa.beam.framework.dataio.ProductWriterPlugIn;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * The product writer for PolSARPro products.
 *
 */
public class PolsarProProductWriter extends EnviProductWriter {

    private final static String BIN_EXTENSION = ".bin";

    /**
     * Construct a new instance of a product writer for the given ENVI product writer plug-in.
     *
     * @param writerPlugIn the given ENVI product writer plug-in, must not be <code>null</code>
     */
    public PolsarProProductWriter(final ProductWriterPlugIn writerPlugIn) {
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
        super.writeProductNodesImpl();

        writeConfigFile(getSourceProduct(), getOutputDir());
    }

    protected String createImageFilename(Band band) {
        return band.getName() + BIN_EXTENSION;
    }

    private static void writeConfigFile(final Product srcProduct, final File folder) {
        PrintStream p = null;
        try {
            final File file = new File(folder, "config.txt");
            final FileOutputStream out = new FileOutputStream(file);
            p = new PrintStream(out);

            p.println("Nrow");
            p.println(srcProduct.getSceneRasterHeight());
            p.println("---------");

            p.println("Ncol");
            p.println(srcProduct.getSceneRasterWidth());
            p.println("---------");

            p.println("PolarCase");
            p.println("monostatic");
            p.println("---------");

            p.println("PolarType");
            p.println(getPolarType(srcProduct.getMetadataRoot()));
            p.println("---------");

        } catch(Exception e) {
            System.out.println("PolsarProWriter unable to write config.txt "+e.getMessage());
        } finally {
            if(p != null)
                p.close();
        }
    }

    private static String getPolarType(final MetadataElement root) {
        if(root != null) {
            final MetadataElement absRoot = root.getElement("Abstracted_Metadata");
            if(absRoot != null) {
                final String pol1 = absRoot.getAttributeString("mds1_tx_rx_polar", "").trim();
                final String pol2 = absRoot.getAttributeString("mds2_tx_rx_polar", "").trim();
                final String pol3 = absRoot.getAttributeString("mds3_tx_rx_polar", "").trim();
                final String pol4 = absRoot.getAttributeString("mds4_tx_rx_polar", "").trim();
                if(!pol1.isEmpty() && !pol2.isEmpty()) {
                    if(!pol3.isEmpty() && !pol4.isEmpty()) {
                        return "full";
                    }
                    return "dual";
                }
            }
        }
        return "single";
    }
}