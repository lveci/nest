
package org.esa.nest.dataio.ceos;

import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.StringUtils;
import org.esa.nest.dataio.ceos.records.BaseRecord;
import org.esa.nest.dataio.ceos.records.FilePointerRecord;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Calendar;
import java.util.ArrayList;

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

    public static FilePointerRecord[] readFilePointers(final BaseRecord vdr, String mission) throws
                                                                                         IllegalCeosFormatException,
                                                                                         IOException {
        final int numFilePointers = vdr.getAttributeInt("Number of filepointer records");
        final CeosFileReader reader = vdr.getReader();
        reader.seek(vdr.getRecordLength());
        final FilePointerRecord[] filePointers = new FilePointerRecord[numFilePointers];
        for (int i = 0; i < numFilePointers; i++) {
            filePointers[i] = new FilePointerRecord(reader, mission);
        }
        return filePointers;
    }

    public static File getCEOSFile(File baseDir, String prefix) throws IOException {
        File[] fileList = baseDir.listFiles();
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
        String name = textRecord.getAttributeString("Scene identification").trim() + '-' +
                textRecord.getAttributeString("Product type specifier").trim().replace("PRODUCT:", "");
        return StringUtils.createValidName(name, new char[]{'_', '-', '.'}, '_');
    }

    public static ProductData.UTC createUTCDate(final int year, final int dayOfYear, final int millisInDay) {
        final Calendar calendar = ProductData.UTC.createCalendar();

        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.DAY_OF_YEAR, dayOfYear);
        calendar.add(Calendar.MILLISECOND, millisInDay);

        return ProductData.UTC.create(calendar.getTime(), 0);
    }

    public static double[] sortToFXYSumOrder(final double[] coeffs) {
        final double[] newOrder = new double[coeffs.length];
        newOrder[0] = coeffs[0];
        newOrder[1] = coeffs[1];
        newOrder[2] = coeffs[2];
        newOrder[3] = coeffs[4];
        newOrder[4] = coeffs[3];
        newOrder[5] = coeffs[5];
        newOrder[6] = coeffs[8];
        newOrder[7] = coeffs[6];
        newOrder[8] = coeffs[7];
        newOrder[9] = coeffs[9];

        return newOrder;
    }

    public static double[] convertLongToDouble(final long[] longs) {
        final double[] doubles = new double[longs.length];
        for (int i = 0; i < longs.length; i++) {
            doubles[i] = Double.longBitsToDouble(longs[i]);
        }
        return doubles;
    }
}
