
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;
import org.esa.beam.framework.datamodel.MetadataElement;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class FilePointerRecordTest extends TestCase {

    private ImageOutputStream _ios;
    private String _prefix;
    private CeosFileReader _reader;

    protected void setUp() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        _ios = new MemoryCacheImageOutputStream(os);
        _prefix = "fdkjglsdkfhierr.m b9b0970w34";
        _ios.writeBytes(_prefix);
        writeRecordData(_ios);
        _ios.writeBytes("nq3tf9ß8nvnvpdi er 0 324p3f"); // as suffix
        _reader = new CeosFileReader(_ios);
    }

    public void testInit_SimpleConstructor() throws IOException, IllegalCeosFormatException {
        _ios.seek(_prefix.length());
        final FilePointerRecord record = new FilePointerRecord(_reader, "ers");

        assertRecord(record);
        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + 360, _ios.getStreamPosition());
    }

    public void testInit() throws IOException, IllegalCeosFormatException {
        final FilePointerRecord record = new FilePointerRecord(_reader, "ers", _prefix.length());

        assertRecord(record);
        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + 360, _ios.getStreamPosition());
    }

    public void testAssignMetadataTo() throws IOException,
                                              IllegalCeosFormatException {
        final FilePointerRecord record = new FilePointerRecord(_reader, "ers", _prefix.length());
        final MetadataElement elem = new MetadataElement("elem");

        record.assignMetadataTo(elem, "suffix");

        assertEquals(0, elem.getNumAttributes());
        assertEquals(1, elem.getNumElements());
        final MetadataElement recordRoot = elem.getElement("FilePointerRecord suffix");
        assertNotNull(recordRoot);
        assertMetadata(recordRoot);
        assertEquals(0, recordRoot.getNumElements());
        assertEquals(22, recordRoot.getNumAttributes());
    }

    public static void assertMetadata(final MetadataElement elem) {
        BaseRecordTest.assertMetadata(elem);

        BaseRecordTest.assertStringAttribute(elem, "Ascii code character", "A ");
        BaseRecordTest.assertIntAttribute(elem, "File pointer number", 2);
        BaseRecordTest.assertStringAttribute(elem, "File ID", "AL PSMB2IMGYBSQ ");
        BaseRecordTest.assertStringAttribute(elem, "File class", "IMAGERY                     ");
        BaseRecordTest.assertStringAttribute(elem, "File class code", "IMGY");
        BaseRecordTest.assertStringAttribute(elem, "File datatype", "BINARY ONLY                 ");
        BaseRecordTest.assertStringAttribute(elem, "File datatype code", "BINO");
        BaseRecordTest.assertIntAttribute(elem, "Number of records", 14001);
        BaseRecordTest.assertIntAttribute(elem, "FirstRecordLength", 897623);
        BaseRecordTest.assertIntAttribute(elem, "MaxRecordLength", 8634264);
        BaseRecordTest.assertStringAttribute(elem, "RecordLengthType", "FIXED LENGTH");
        BaseRecordTest.assertStringAttribute(elem, "RecordLengthTypeCode", "FIXD");
        BaseRecordTest.assertIntAttribute(elem, "FirstRecordVolumeNumber", 1);
        BaseRecordTest.assertIntAttribute(elem, "FinalRecordVolumeNumber", 2);
        BaseRecordTest.assertIntAttribute(elem, "ReferencedFilePortionStart", 3);
        BaseRecordTest.assertIntAttribute(elem, "ReferencedFilePortionEnd", 17);
    }

    private static void assertRecord(final FilePointerRecord record) {
        BaseRecordTest.assertRecord(record);

        assertNotNull(record);
        assertEquals("A ", record.getAttributeString("Ascii code character"));
        assertEquals(2, record.getAttributeInt("File Pointer Number"));
        assertEquals("AL PSMB2IMGYBSQ ", record.getAttributeString("File ID"));
        assertEquals("IMAGERY                     ", record.getAttributeString("File class"));
        assertEquals("IMGY", record.getAttributeString("File class code"));
        assertEquals("BINARY ONLY                 ", record.getAttributeString("File datatype"));
        assertEquals("BINO", record.getAttributeString("File datatype Code"));
        assertEquals(14001, record.getAttributeInt("Number of records"));
        assertEquals(897623, record.getAttributeInt("FirstRecordLength"));
        assertEquals(8634264, record.getAttributeInt("MaxRecordLength"));
        assertEquals("FIXED LENGTH", record.getAttributeString("RecordLengthType"));
        assertEquals("FIXD", record.getAttributeString("RecordLengthTypeCode"));
        assertEquals(1, record.getAttributeInt("FirstRecordVolumeNumber"));
        assertEquals(2, record.getAttributeInt("FinalRecordVolumeNumber"));
        assertEquals(3, record.getAttributeInt("ReferencedFilePortionStart"));
        assertEquals(17, record.getAttributeInt("ReferencedFilePortionEnd"));
    }

    private static void writeRecordData(ImageOutputStream ios) throws IOException {
        BaseRecordTest.writeRecordData(ios);
        ios.writeBytes("A "); // codeCharacter = "A" + 1 blank // A2
        ios.skipBytes(2); // reader.skipBytes(2);  // blank
        ios.writeBytes("   2"); // filePointerNumber = bbb1 - bbb9 // I4
        ios.writeBytes("AL PSMB2IMGYBSQ "); // fileID = "LL SSSCTFFFFXXXB"reader.readAn(16);
        ios.writeBytes("IMAGERY                     "); // fileClass // A28
        ios.writeBytes("IMGY"); // fileClassCode = "LEAD", "IMGY", "TRAI" or "SPPL"  // A4
        // fileDataType = "BINARY ONLY                 " or "MIXED BINARY AND ASCII      "
        ios.writeBytes("BINARY ONLY                 "); // A28
        ios.writeBytes("BINO"); // fileDataTypeCode = "MBAA" or "BINO" // A4
        ios.writeBytes("   14001"); // numberOfRecords = 2 - n+1 // I8
        ios.writeBytes("  897623"); // _firstRecordLength // I8
        ios.writeBytes(" 8634264"); // _maxRecordLength  // I8
        ios.writeBytes("FIXED LENGTH"); // _recordLengthType = "FIXED LENGTH" // A12
        ios.writeBytes("FIXD"); // _recordLengthTypeCode = "FIXD" // A4
        ios.writeBytes(" 1"); // _firstRecordVolumeNumer = 1 // I2
        ios.writeBytes(" 2"); // _finalRecordVolumeNumber = 1 (2 only for test)// I2
        ios.writeBytes("       3"); // ReferencedFilePortionStart = 1 (3 only for test) // I8
        ios.writeBytes("      17"); // ReferencedFilePortionEnd = 1 (3 only for test) // I8

        // Blank = 208 blanks
        ios.writeBytes("                                                  " +
                       "                                                  " +
                       "                                                  " +
                       "                                                  "); // A208
    }
}
