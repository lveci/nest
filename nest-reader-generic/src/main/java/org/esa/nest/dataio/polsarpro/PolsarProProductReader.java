package org.esa.nest.dataio.polsarpro;

import org.esa.beam.dataio.envi.Header;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.Product;
import org.esa.nest.dataio.ReaderUtils;
import org.esa.nest.dataio.envi.NestEnviProductReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

class PolsarProProductReader extends NestEnviProductReader {

    PolsarProProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        final File inputFile = ReaderUtils.getFileFromInput(getInput());
        final File[] fileList;
        if(inputFile.isDirectory())
            fileList = inputFile.listFiles();
        else
            fileList = new File[] { inputFile };

        final ArrayList<Header> headerList = new ArrayList<Header>();
        final HashMap<Header, File> headerFileMap = new HashMap<Header, File>();
        Header mainHeader = null;
        File mainHeaderFile = null;

        for(File file : fileList) {
            if(file.isDirectory())
                continue;
            if(file.getName().toLowerCase().endsWith("hdr")) {
                final File imgFile = createEnviImageFile(file);
                if(!imgFile.exists())
                    continue;
                
                final BufferedReader headerReader = getHeaderReader(file);
                try {

                    synchronized (headerReader) {
                        final Header header = new Header(headerReader);
                        headerList.add(header);
                        headerFileMap.put(header, file);

                        if(header.getNumBands() > 0 && header.getBandNames() != null) {
                            mainHeader = header;
                            mainHeaderFile = file;
                        }
                    }

                } finally {
                    if (headerReader != null) {
                        headerReader.close();
                    }
                }
            }
        }

        if(mainHeader == null)
            throw new IOException("Unable to read files");

        final String productName;
        if(inputFile.isDirectory()) {
            productName = inputFile.getName();
        } else {
            final String headerFileName = mainHeaderFile.getName();
            productName = headerFileName.substring(0, headerFileName.indexOf('.'));
        }

        final Product product = new Product(productName, mainHeader.getSensorType(),
                mainHeader.getNumSamples(), mainHeader.getNumLines());
        product.setProductReader(this);
        product.setFileLocation(mainHeaderFile);
        product.setDescription(mainHeader.getDescription());

        initGeocoding(product, mainHeader);

        for(Header header : headerList) {
            initBands(product, headerFileMap.get(header), header);
        }

        applyBeamProperties(product, mainHeader.getBeamProperties());

        initMetadata(product, mainHeaderFile);

        return product;
    }

}