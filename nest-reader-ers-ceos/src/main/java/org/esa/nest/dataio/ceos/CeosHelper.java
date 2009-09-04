
package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.StringUtils;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.FilePointerRecord;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Calendar;

public class CeosHelper {

    private static final String VOLUME_DESC_FILE_PREFIX = "VDF_";
    private static final String VOLUME_FILE_PREFIX = "VOL-";
    private static final String LEADER_FILE_PREFIX = "LEA_";
    private static final String IMAGE_FILE_PREFIX = "DAT_";
    private static final String TRAILER_FILE_PREFIX = "NUL_";

    public static File getVolumeFile(final File baseDir) throws IOException {
        final File[] files = baseDir.listFiles(new FilenameFilter() {
            public boolean accept(final File dir, final String name) {
                return name.toUpperCase().startsWith(VOLUME_DESC_FILE_PREFIX) ||
                        name.toUpperCase().startsWith(VOLUME_FILE_PREFIX);
            }
        });
        if (files == null || files.length < 1) {
            throw new IOException("No volume descriptor file found in directory:\n"
                                  + baseDir.getPath());
        }
        if (files.length > 1) {
            throw new IOException("Multiple volume descriptor files found in directory:\n"
                                  + baseDir.getPath());
        }
        return files[0];
    }

    public static FilePointerRecord[] readFilePointers(final BaseRecord vdr, final String mission)
            throws IllegalBinaryFormatException, IOException {
        final int numFilePointers = vdr.getAttributeInt("Number of filepointer records");
        final BinaryFileReader reader = vdr.getReader();
        reader.seek(vdr.getRecordLength());
        final FilePointerRecord[] filePointers = new FilePointerRecord[numFilePointers];
        for (int i = 0; i < numFilePointers; i++) {
            filePointers[i] = new FilePointerRecord(reader, mission);
        }
        return filePointers;
    }

    public static File getCEOSFile(final File baseDir, final String prefix) throws IOException {
        final File[] fileList = baseDir.listFiles();
        for (File file : fileList) {
            if (file.getName().toUpperCase().startsWith(prefix))
                return file;
        }
        throw new IOException("unable to find file starting with " + prefix);
    }

    public static String getImageFileName(final BaseRecord textRecord, final String ccd) {
        if (ccd != null && ccd.trim().length() > 0) {
            return IMAGE_FILE_PREFIX + '0' + ccd + '-' + getProductName(textRecord);
        } else {
            return IMAGE_FILE_PREFIX + getProductName(textRecord);
        }
    }

    public static String getProductName(final BaseRecord textRecord) {
        final String name = textRecord.getAttributeString("Product type specifier").trim().replace("PRODUCT:", "")
                + '-' + textRecord.getAttributeString("Scene identification").trim();
        return StringUtils.createValidName(name.trim(), new char[]{'_', '-', '.'}, '_');
    }

    public static String getProductType(final BaseRecord textRecord) {
        final String type = textRecord.getAttributeString("Product type specifier").trim();
        return type.replace("PRODUCT:", "").trim();
    }

    public static ProductData.UTC createUTCDate(final int year, final int dayOfYear, final int millisInDay) {
        final Calendar calendar = ProductData.UTC.createCalendar();

        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
        calendar.add(Calendar.MILLISECOND, millisInDay);

        return ProductData.UTC.create(calendar.getTime(), 0);
    }

    public static void addMetadata(MetadataElement sphElem, BaseRecord rec, String name) {
        if(rec != null) {
            final MetadataElement metadata = new MetadataElement(name);
            rec.assignMetadataTo(metadata);
            sphElem.addElement(metadata);
        }
    }

}
