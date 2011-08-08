package org.esa.beam.dataio.envi;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class EnviProductReaderTest_WithFileIO extends TestCase {

    private File tempDir;

    @Override
    protected void setUp() throws Exception {
        final String ioTempDir = System.getProperty("java.io.tmpdir");
        tempDir = new File(ioTempDir, "tempEnviProductReaderTest");
        assertEquals(true, tempDir.mkdir());
    }

    @Override
    protected void tearDown() throws Exception {
        TestUtils.deleteFileTree(tempDir);
    }

    public void testCreateEnviImageFile() throws IOException {
        final File hdrFile1 = new File(tempDir, "envifile.img.hdr");
        final File hdrFile2 = new File(tempDir, "envifile.hdr");

        final File expImgFile = new File(tempDir, "envifile.img");
        assertEquals(true, expImgFile.createNewFile());
        assertEquals(expImgFile, EnviProductReader.getEnviImageFile(hdrFile1));
        assertEquals(expImgFile, EnviProductReader.getEnviImageFile(hdrFile2));
    }

    public void testCreateEnviImageFile_img_File_not_exist() throws IOException {
        final File hdrFile1 = new File(tempDir, "envifile.img.hdr");
        final File hdrFile2 = new File(tempDir, "envifile.hdr");

        final File expImgFile = new File(tempDir, "envifile.img");
        assertEquals(false, expImgFile.exists());
        final File expBinFile = new File(tempDir, "envifile.bin");
        assertEquals(true, expBinFile.createNewFile());
        assertEquals(expBinFile, EnviProductReader.getEnviImageFile(hdrFile1));
        assertEquals(expBinFile, EnviProductReader.getEnviImageFile(hdrFile2));
    }
}