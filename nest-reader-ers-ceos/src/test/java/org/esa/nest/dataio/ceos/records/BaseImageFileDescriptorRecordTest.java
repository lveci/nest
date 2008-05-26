
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosTestHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class BaseImageFileDescriptorRecordTest extends TestCase {

    private MemoryCacheImageOutputStream _ios;
    private String _prefix;
    private CeosFileReader _reader;

    protected void setUp() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        _ios = new MemoryCacheImageOutputStream(os);
        _prefix = "AbstractImageFileDescriptorRecordTest_prefix";
        _ios.writeBytes(_prefix);
        writeRecordData(_ios);
        _ios.writeBytes("AbstractImageFileDescriptorRecordTest_suffix"); // as suffix
        _reader = new CeosFileReader(_ios);
    }

    public void testInit_SimpleConstructor() throws IOException,
                                                    IllegalCeosFormatException {
        _reader.seek(_prefix.length());

        final BaseImageFileDescriptorRecord record = createImageFileDescriptorRecord(_reader);

        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + BaseRecordTest.RECORD_LENGTH, _ios.getStreamPosition());
        assertRecord(record);
    }

    public void testInit() throws IOException,
                                  IllegalCeosFormatException {
        final BaseImageFileDescriptorRecord record = createImageFileDescriptor(_reader, _prefix.length());

        assertEquals(_prefix.length(), record.getStartPos());
        assertEquals(_prefix.length() + BaseRecordTest.RECORD_LENGTH, _ios.getStreamPosition());
        assertRecord(record);
    }

    public static void assertCommonRecord(final BaseRecord record) {
        BaseRecordTest.assertRecord(record);

        assertEquals("A ", record.getAttributeString("Ascii code character"));
        assertEquals("manno       ", record.getAttributeString("Specification number")); // fileDocumentNumber // A12
        assertEquals("A ", record.getAttributeString("Specification revision number")); // fileDokumentRevisionNumber // A2
        assertEquals("A ", record.getAttributeString("Record format revision number")); // fileDesignRevisionLetter // A2
        assertEquals("prdsys      ", record.getAttributeString("Software version number")); // logicalVolPrepSysRelNum //A12
        assertEquals(1, record.getAttributeInt("File Number")); // fileNumber // I4
        assertEquals("AL PSMB2LEADBSQ ", record.getAttributeString("File Name")); // fileID // A16
        assertEquals("FSEQ", record.getAttributeString("Record Sequence and Location Type Flag")); // flagRecordComposition // A4
        assertEquals(1, record.getAttributeInt("Sequence Number Location")); // recordNumberPositionOfEachFile // I8
        assertEquals(4, record.getAttributeInt("Sequence number field length")); // fieldLengthForRecordData // I4
        assertEquals("FTYP", record.getAttributeString("Record code and location type flag")); // flagOfRecordTypeCode // A4
        assertEquals(5, record.getAttributeInt("Record code location")); // recordTypeCodeBytePosition // I8
        assertEquals(4, record.getAttributeInt("Record code field length")); // recordTypeCodeFieldLength // I4
        assertEquals("FLGT", record.getAttributeString("Record length and location type flag")); // flagRecordLength // A4
        assertEquals(9, record.getAttributeInt("Record length location")); // bytePosOfRecLenth // I8
        assertEquals(4, record.getAttributeInt("Record length field length")); // numOfBytesOfRecLength // I4
    }

    public static void writeCommonRecordData(ImageOutputStream ios) throws IOException {
        BaseRecordTest.writeRecordData(ios);

        ios.writeBytes("A "); // codeCharacter = "A" + 1 blank // A2
        ios.writeBytes("  "); // A2 // 2 blanks
        ios.writeBytes("manno       "); // file document number // A12
        ios.writeBytes("A "); // fileDokumentRevisionNumber // A2
        ios.writeBytes("A "); // fileDesignRevisionLetter // A2
        ios.writeBytes("prdsys      "); // logicalVolPrepSysRelNum //A12
        ios.writeBytes("   1"); // fileNumber // I4
        ios.writeBytes("AL PSMB2LEADBSQ "); // fileID // A16
        ios.writeBytes("FSEQ");  // flagRecordComposition = "FSEQ" // A4
        ios.writeBytes("       1"); // recordNumberPositionOfEachFile = "bbbbbbb1" // I8
        ios.writeBytes("   4");  // fieldLengthForRecordData ="bbb4" // I4
        ios.writeBytes("FTYP"); // flagOfRecordTypeCode = "FTYP" // A4
        ios.writeBytes("       5"); // recordTypeCodeBytePosition ="bbbbbbb5" // I8
        ios.writeBytes("   4"); // recordTypeCodeFieldLength = "bbb4" // I4
        ios.writeBytes("FLGT"); // flagRecordLength = "FLGT" // A4
        ios.writeBytes("       9"); // bytePosOfRecLenth = "bbbbbbb9" // I8
        ios.writeBytes("   4"); // numOfBytesOfRecLength = "bbb4" // I4
        ios.writeBytes("N"); // flagDataConvInfFileDescRec = "N" // A1
        ios.writeBytes("O"); // flagDataConvInOtherRecords = "N" (for test "O") // A1
        ios.writeBytes("P"); // flagDataDispFileDescRecord = "N" (for test "P") // A1
        ios.writeBytes("Q"); // flagDataDispInOtherRecords = "N" (for test "Q") // A1

        CeosTestHelper.writeBlanks(ios, 64);
    }

    protected void writeRecordData(final ImageOutputStream ios) throws IOException {
        writeCommonRecordData(ios);

        ios.writeBytes(" 12546"); // numImageRecords // I6
        ios.writeBytes(" 12487"); // imageRecordLength // I6
        CeosTestHelper.writeBlanks(ios, 24);
        ios.writeBytes(" 123"); // numBitsPerPixel // I4
        ios.writeBytes(" 234"); // numPixelsPerData // I4
        ios.writeBytes(" 345"); // numBytesPerData // I4
        ios.writeBytes("abcd"); // bitlistOfPixel // A4
        ios.writeBytes(" 567"); // numBandsPerFile // I4
        ios.writeBytes("14587962"); // numLinesPerBand // I8
        ios.writeBytes("1245"); // numLeftBorderPixelsPerLine // I4
        ios.writeBytes("24568954"); // numImagePixelsPerLine // I8
        ios.writeBytes("6542"); // numRightBorderPixelsPerLine // I4
        ios.writeBytes("5432"); // numTopBorderLines // I4
        ios.writeBytes("4321"); // numBottomBorderLines // I4
        ios.writeBytes("bcde"); // imageFormatID // A4
        ios.writeBytes(" 852"); // numRecordsPerLineSingleUnit // I4
        ios.writeBytes(" 963"); // numRecordsPerLine // I4
        ios.writeBytes(" 741"); // numBytesCoverIdentifierAndHeader // I4
        ios.writeBytes("24562583"); // numImgDataBytesPerRecAndDummyPix // I8
        ios.writeBytes(" 987"); // numBytesOfSuffixDataPerRecord // I4
        ios.writeBytes("sdef"); // flagPrefixDataRepeat // A4
        ios.writeBytes("   1 4PB"); // locatorLineNumber
        ios.writeBytes("   5 4PB"); // locatorBandNumber
        ios.writeBytes("   9 6PB"); // locatorScanStartTime
        ios.writeBytes("  15 4PB"); // locatorLeftDummyPixel
        ios.writeBytes("  19 4PB"); // locatorRightDummyPixel

        writeBytes341To392(ios);

        ios.writeBytes("oiklfdöklsgjopesirmfdlknaoiawefölkdd"); // dataFormatTypeId // A36
        ios.writeBytes("BVFR"); // dataFormatTypeIdCode // A4
        ios.writeBytes(" 753"); // numLeftUnusedBitsInPixelData // I4
        ios.writeBytes(" 357"); // numRightUnusedBitsInPixelData // I4
        ios.writeBytes(" 242"); // maxPixelDataValue // I4
        CeosTestHelper.writeBlanks(ios, 4);
        CeosTestHelper.writeBlanks(ios, 8);
        CeosTestHelper.writeBlanks(ios, 8);
        CeosTestHelper.writeBlanks(ios, BaseRecordTest.RECORD_LENGTH - 464);
    }

    protected void assertRecord(final BaseImageFileDescriptorRecord record) {
        assertCommonRecord(record);

        assertEquals(12546, record.getAttributeInt("Number of SAR DATA records")); //getNumImageRecords());
        assertEquals(12487, record.getAttributeInt("SAR DATA record length")); //getImageRecordLength());
        assertEquals(123, record.getAttributeInt("Number of bits per sample")); //getNumBitsPerPixel());
        assertEquals(234, record.getAttributeInt("")); //getNumPixelsPerData());
        assertEquals(345, record.getAttributeInt("")); //getNumBytesPerData());
        assertEquals("abcd", record.getAttributeString("")); //getBitlistOfPixel());
        assertEquals(567, record.getAttributeInt("")); //getNumBandsPerFile());
        assertEquals(14587962, record.getAttributeInt("")); //getNumLinesPerBand());
        assertEquals(1245, record.getAttributeInt("")); //getNumLeftBorderPixelsPerLine());
        assertEquals(24568954, record.getAttributeInt("")); //getNumImagePixelsPerLine());
        assertEquals(6542, record.getAttributeInt("")); //getNumRightBorderPixelsPerLine());
        assertEquals(5432, record.getAttributeInt("")); //getNumTopBorderLines());
        assertEquals(4321, record.getAttributeInt("")); //getNumBottomBorderLines());
        assertEquals("bcde", record.getAttributeString("")); //getImageFormatID());
        assertEquals(852, record.getAttributeInt("")); //getNumRecordsPerLineSingleUnit());
        assertEquals(963, record.getAttributeInt("")); //getNumRecordsPerLine());
        assertEquals(741, record.getAttributeInt("")); //getNumBytesCoverIdentifierAndHeader());
        assertEquals(24562583, record.getAttributeInt("")); //getNumImgDataBytesPerRecAndDummyPix());
        assertEquals(987, record.getAttributeInt("")); //getNumBytesOfSuffixDataPerRecord());
        assertEquals("sdef", record.getAttributeString("")); //getFlagPrefixDataRepeat());
        assertEquals("   1 4PB", record.getAttributeString("")); //getLocatorLineNumber());
        assertEquals("   5 4PB", record.getAttributeString("")); //getLocatorBandNumber());
        assertEquals("   9 6PB", record.getAttributeString("")); //getLocatorScanStartTime());
        assertEquals("  15 4PB", record.getAttributeString("")); //getLocatorLeftDummyPixel());
        assertEquals("  19 4PB", record.getAttributeString("")); //getAttributeInt(""); //getLocatorRightDummyPixel());

        assertBytes341To392(record);

        assertEquals("oiklfdöklsgjopesirmfdlknaoiawefölkdd", record.getAttributeString("")); //getDataFormatTypeId());
        assertEquals("BVFR", record.getAttributeString("")); //getDataFormatTypeIdCode());
        assertEquals(753, record.getAttributeInt("")); //getNumLeftUnusedBitsInPixelData());
        assertEquals(357, record.getAttributeInt("")); //getNumRightUnusedBitsInPixelData());
        assertEquals(242, record.getAttributeInt("")); //getMaxPixelDataValue());
    }


    protected abstract BaseImageFileDescriptorRecord createImageFileDescriptorRecord(final CeosFileReader reader) throws
                                                                                                                  IOException,
                                                                                                                  IllegalCeosFormatException;

    protected abstract BaseImageFileDescriptorRecord createImageFileDescriptor(final CeosFileReader reader,
                                                                               final int startPos) throws IOException,
                                                                                                          IllegalCeosFormatException;

    protected abstract void writeBytes341To392(ImageOutputStream ios) throws IOException;

    protected abstract void assertBytes341To392(final BaseImageFileDescriptorRecord record);
}
