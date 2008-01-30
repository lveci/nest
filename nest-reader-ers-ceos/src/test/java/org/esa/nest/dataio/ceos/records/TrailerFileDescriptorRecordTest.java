
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosTestHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class TrailerFileDescriptorRecordTest extends TestCase {

    private String _prefix;
    private CeosFileReader _reader;

    protected void setUp() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(os);
        _prefix = "TrailerFileDescriptorRecordTest_prefix";
        ios.writeBytes(_prefix);
        writeRecordData(ios);
        ios.writeBytes("TrailerFileDescriptorRecordTest_suffix"); // as suffix
        _reader = new CeosFileReader(ios);
    }

    public void testInit_SimpleConstructor() throws IOException,
                                                    IllegalCeosFormatException {
        _reader.seek(_prefix.length());

        final TrailerFileDescriptorRecord record = new TrailerFileDescriptorRecord(_reader);

        assertRecord(record);
    }

    public void testInit() throws IOException,
                                  IllegalCeosFormatException {
        final TrailerFileDescriptorRecord record = new TrailerFileDescriptorRecord(_reader, _prefix.length());

        assertRecord(record);
    }

    private void writeRecordData(final ImageOutputStream ios) throws IOException {
        CommonFileDescriptorRecordTest.writeRecordData(ios);

        ios.writeBytes("     1"); // number of trailer records // I6
        ios.writeBytes("  8460"); // trailer record length // I6
        CeosTestHelper.writeBlanks(ios, 24);
        CeosTestHelper.writeBlanks(ios, 8244);
    }

    private void assertRecord(final TrailerFileDescriptorRecord record) {
        CommonFileDescriptorRecordTest.assertRecord(record);

        assertEquals(1, record.getNumTrailerRecords());
        assertEquals(8460, record.getTrailerRecordLength());
    }
}
