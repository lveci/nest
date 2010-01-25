package org.esa.beam.dataio.envi;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.util.io.BeamFileFilter;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class EnviProductReaderPlugIn implements ProductReaderPlugIn {

    public DecodeQualification getDecodeQualification(Object input) {
        if (input instanceof ImageInputStream) {
            return checkDecodeQualificationOnStream((ImageInputStream) input);
        } else if (input instanceof File) {
            return checkDecodeQualificationOnFile((File) input);
        } else if (input instanceof String) {
            final String fileName = (String) input;
            return checkDecodeQualificationOnFile(new File(fileName));
        }

        return DecodeQualification.UNABLE;
    }

    public String[] getFormatNames() {
        return new String[]{EnviConstants.FORMAT_NAME};
    }

    public ProductReader createReaderInstance() {
        return new EnviProductReader(this);
    }

    public String[] getDefaultFileExtensions() {
        return EnviConstants.VALID_EXTENSIONS;
    }

    public Class[] getInputTypes() {
        return new Class[]{String.class, File.class};
    }

    public String getDescription(Locale locale) {
        return EnviConstants.DESCRIPTION;
    }

    public BeamFileFilter getProductFileFilter() {
        return new BeamFileFilter(getFormatNames()[0], getDefaultFileExtensions(), getDescription(null));
    }

    ///////////////////////////////////////////////////////////////////////////
    /////// END OF PUBLIC
    ///////////////////////////////////////////////////////////////////////////

    static File getInputFile(Object input) {
        File file = null;
        if (input instanceof String) {
            file = new File((String) input);
        } else if (input instanceof File) {
            file = (File) input;
        }
        return file;
    }


    static boolean isCompressedFile(File file) {
        return file.getName().lastIndexOf('.' + EnviConstants.ZIP) > -1;
    }

    static InputStream getHeaderStreamFromZip(ZipFile productZip) throws IOException {
        final Enumeration entries = productZip.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry zipEntry = (ZipEntry) entries.nextElement();
            final String name = zipEntry.getName();
            if (name.indexOf(".hdr") > 0) {
                return productZip.getInputStream(zipEntry);
            }
        }
        return null;
    }

    private static DecodeQualification checkDecodeQualificationOnStream(ImageInputStream headerStream) {
        try {
            final String line = headerStream.readLine();
            if (line != null && line.startsWith(EnviConstants.FIRST_LINE)) {
                return DecodeQualification.INTENDED;
            }

        } catch (IOException ignore) {
            // intentionally nothing in here tb 20080409
        }
        return DecodeQualification.UNABLE;
    }

    private static DecodeQualification checkDecodeQualificationOnStream(InputStream headerStream) {
        try {
            final BufferedReader inReader = new BufferedReader(new InputStreamReader(headerStream));
            final String line = inReader.readLine();
            if (line != null && line.startsWith(EnviConstants.FIRST_LINE)) {
                return DecodeQualification.INTENDED;
            }
        } catch (IOException ignore) {
            // intentionally nothing in here tb 20080409
        }

        return DecodeQualification.UNABLE;
    }


    private static DecodeQualification checkDecodeQualificationOnFile(File headerFile) {
        try {
            final String fileName = headerFile.getName().toLowerCase();
            boolean validExt = false;
            for(String ext : EnviConstants.VALID_EXTENSIONS) {
                if(fileName.endsWith(ext)) {
                    validExt = true;
                    break;
                }
            }
            if(!validExt) return DecodeQualification.UNABLE;

            if (isCompressedFile(headerFile)) {
                final ZipFile productZip = new ZipFile(headerFile, ZipFile.OPEN_READ);

                if (productZip.size() != 2) {
                    productZip.close();
                    return DecodeQualification.UNABLE;
                }

                final InputStream headerStream = getHeaderStreamFromZip(productZip);
                if (headerStream != null) {
                    final DecodeQualification result = checkDecodeQualificationOnStream(headerStream);
                    productZip.close();
                    return result;
                }
                productZip.close();
            } else {
                ImageInputStream headerStream = new FileImageInputStream(headerFile);
                final DecodeQualification result = checkDecodeQualificationOnStream(headerStream);
                headerStream.close();
                return result;
            }
        }
        catch (IOException e) {
            // intentionally left empty - returns the same as the line below tb 20080409
        }
        return DecodeQualification.UNABLE;
    }
}
