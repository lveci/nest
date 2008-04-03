
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosTestHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @noinspection OctalInteger
 */
public class TextRecordTest extends TestCase {

    private MemoryCacheImageOutputStream _ios;
    private String _prefix;
    private CeosFileReader _reader;

    protected void setUp() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        _ios = new MemoryCacheImageOutputStream(os);
        _prefix = "TextRecordTest_prefix";
        _ios.writeBytes(_prefix);
        writeRecordData(_ios);
        _ios.writeBytes("TextRecordTest_suffix"); // as suffix
        _reader = new CeosFileReader(_ios);
    }

    public void testInit_SimpleConstructor() throws IOException, IllegalCeosFormatException {
        _reader.seek(_prefix.length());
        final TextRecord textRecord = new TextRecord(_reader);

        assertRecord(textRecord);
    }

    public void testInit() throws IOException, IllegalCeosFormatException {
        final TextRecord textRecord = new TextRecord(_reader, _prefix.length());

        assertRecord(textRecord);
    }

    private void writeRecordData(final ImageOutputStream ios) throws IOException {
        BaseRecordTest.writeRecordData(ios);

        // codeCharacter = "A" + 1 blank
        ios.writeBytes("A "); // A2
        ios.skipBytes(2); // reader.skipBytes(2);  // blank
        // productID = "PRODUCT:ABBBCCDE" + 24 blanks
        ios.writeBytes("PRODUCT:O1B2R_UB                        "); // A40
        // facility = "PROCESS:JAPAN-JAXA-EOC-ALOS-DPS  YYYYMMDDHHNNSS" + 13 blanks
        ios.writeBytes("PROCESS:JAPAN-JAXA-EOC-ALOS-DPS  20060410075225             "); //A60
        
        // Blank = 200 blanks
        CeosTestHelper.writeBlanks(ios, 200);
    }

    private void assertRecord(final TextRecord record) throws IOException {
        BaseRecordTest.assertRecord(record);
        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + 360, _ios.getStreamPosition());

        assertEquals("A ", record.getCodeCharacter());
        assertEquals("O1B2R_UB", record.getProductID());
        assertEquals("PROCESS:JAPAN-JAXA-EOC-ALOS-DPS  20060410075225             ",
                     record.getFacility());

    }
}
