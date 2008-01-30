
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.ceos.CeosFileReader;
import org.esa.nest.dataio.ceos.CeosTestHelper;
import org.esa.nest.dataio.ceos.IllegalCeosFormatException;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CommonFileDescriptorRecordTest extends TestCase {

    public void testInitCommonFileDescriptorRecord() throws IOException,
                                                            IllegalCeosFormatException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        final ImageOutputStream ios = new MemoryCacheImageOutputStream(os);
        final String prefix = "fdkjglsdkfhierr.m b9b0970w34";
        ios.writeBytes(prefix);
        writeRecordData(ios);
        ios.writeBytes("nq3tf9ß8nvnvpdi er 0 324p3f"); // as suffix

        final CommonFileDescriptorRecord record = new CommonFileDescriptorRecord(new CeosFileReader(ios),
                                                                                 prefix.length());

        assertRecord(record);
        assertEquals(prefix.length(), record.getStartPos());
        assertEquals(prefix.length() + 180, ios.getStreamPosition());
    }

    public static void assertRecord(final CommonFileDescriptorRecord record) {
        BaseRecordTest.assertRecord(record);

        assertEquals("A ", record.getCodeCharacter());
        assertEquals("manno       ", record.getFileDocumentNumber()); // fileDocumentNumber // A12
        assertEquals("A ", record.getFileDokumentRevisionNumber()); // fileDokumentRevisionNumber // A2
        assertEquals("A ", record.getFileDesignRevisionLetter()); // fileDesignRevisionLetter // A2
        assertEquals("prdsys      ", record.getLogicalVolPrepSysRelNum()); // logicalVolPrepSysRelNum //A12
        assertEquals(1, record.getFileNumber()); // fileNumber // I4
        assertEquals("AL PSMB2LEADBSQ ", record.getFileID()); // fileID // A16
        assertEquals("FSEQ", record.getFlagRecordComposition()); // flagRecordComposition // A4
        assertEquals(1, record.getRecordNumberPositionOfEachFile()); // recordNumberPositionOfEachFile // I8
        assertEquals(4, record.getFieldLengthForRecordData()); // fieldLengthForRecordData // I4
        assertEquals("FTYP", record.getFlagOfRecordTypeCode()); // flagOfRecordTypeCode // A4
        assertEquals(5, record.getRecordTypeCodeBytePosition()); // recordTypeCodeBytePosition // I8
        assertEquals(4, record.getRecordTypeCodeFieldLength()); // recordTypeCodeFieldLength // I4
        assertEquals("FLGT", record.getFlagRecordLength()); // flagRecordLength // A4
        assertEquals(9, record.getBytePosOfRecLength()); // bytePosOfRecLenth // I8
        assertEquals(4, record.getNumOfBytesOfRecLength()); // numOfBytesOfRecLength // I4
        assertEquals("N", record.getFlagDataConvInfFileDescRec()); // flagDataConvInfFileDescRec // A1
        assertEquals("O", record.getFlagDataConvInOtherRecords()); // flagDataConvInOtherRecords // A1
        assertEquals("P", record.getFlagDataDispFileDescRecord()); // flagDataDispFileDescRecord // A1
        assertEquals("Q", record.getFlagDataDispInOtherRecords()); // flagDataDispInOtherRecords // A1
    }

    public static void writeRecordData(ImageOutputStream ios) throws IOException {
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
}
