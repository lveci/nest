
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosTestHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class Ancillary2RecordTest extends TestCase {

    private static final int RECORD_LENGTH = 4680;

    private MemoryCacheImageOutputStream _ios;
    protected String _prefix;
    private CeosFileReader _reader;

    protected void setUp() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        _ios = new MemoryCacheImageOutputStream(os);
        _prefix = "fdkjglsdkfhierr.m b9b0970w34";
        _ios.writeBytes(_prefix);
        _reader = new CeosFileReader(_ios);
    }

    public void testInitRecord_SimpleConstructor() throws IOException,
                                                          IllegalCeosFormatException {
        writeRecordData(_ios);
        _ios.writeBytes("nq3tf9ß8nvnvpdi er 0 324p3f"); // suffix
        _reader.seek(_prefix.length());

        final Ancillary2Record record = createAncillary2Record(_reader);

        assertRecord(record);
    }

    public void testInitRecord() throws IOException, IllegalCeosFormatException {
        writeRecordData(_ios);
        _ios.writeBytes("nq3tf9ß8nvnvpdi er 0 324p3f"); // suffix


        final Ancillary2Record record = createAncillary2Record(_reader, _prefix.length());

        assertRecord(record);
    }


    protected abstract Ancillary2Record createAncillary2Record(final CeosFileReader reader) throws IOException,
                                                                                                   IllegalCeosFormatException;

    protected abstract Ancillary2Record createAncillary2Record(final CeosFileReader reader, final int startPos) throws
                                                                                                                IOException,
                                                                                                                IllegalCeosFormatException;

    protected abstract void writeSpecificRecordData(MemoryCacheImageOutputStream ios) throws IOException;

    protected abstract void assertSpecificRecordData(final Ancillary2Record record);

    private void writeRecordData(final MemoryCacheImageOutputStream ios) throws IOException {
        BaseRecordTest.writeRecordData(ios);

        ios.writeBytes("OB4 "); // getSensorOperationMode // A4
        ios.writeBytes("1234"); // lowerLimitOfStrengthAfterCorrection // I4
        ios.writeBytes("3241"); // upperLimitOfStrengthAfterCorrection // I4
        CeosTestHelper.writeBlanks(ios, 32);
        ios.writeBytes("abcdef"); // sensorGains // A6
        writeSpecificRecordData(ios);
    }

    private void assertRecord(final Ancillary2Record record) throws IOException {
        BaseRecordTest.assertRecord(record);
        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + RECORD_LENGTH, _ios.getStreamPosition());

        assertEquals("OB4 ", record.getSensorOperationMode());
        assertEquals(1234, record.getLowerLimitOfStrengthAfterCorrection());
        assertEquals(3241, record.getUpperLimitOfStrengthAfterCorrection());
        assertEquals("abcdef", record.getSensorGains());

        assertSpecificRecordData(record);
    }

}
