package org.esa.beam.dataio.envi;

import junit.framework.TestCase;
import org.esa.beam.util.SystemUtils;

import java.io.File;
import java.io.IOException;

public class EnviProductReaderTest_WithFileIO extends TestCase {

    private File tempDir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final String workingDir = SystemUtils.getCurrentWorkingDir().getAbsolutePath();
        tempDir = new File(workingDir, "tempEnviProductReaderTest");
        assertEquals(true, tempDir.mkdir());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestUtils.deleteFileTree(tempDir);
    }

    public void testCreateEnviImageFile() throws IOException {
        final File hdrFile1 = new File(tempDir, "envifile.img.hdr");
        final File hdrFile2 = new File(tempDir, "envifile.hdr");

        final File expImgFile = new File(tempDir, "envifile.img");
        assertEquals(true, expImgFile.createNewFile());
        assertEquals(expImgFile, EnviProductReader.createEnviImageFile(hdrFile1));
        assertEquals(expImgFile, EnviProductReader.createEnviImageFile(hdrFile2));
    }

    public void testCreateEnviImageFile_img_File_not_exist() {
        final File hdrFile1 = new File(tempDir, "envifile.img.hdr");
        final File hdrFile2 = new File(tempDir, "envifile.hdr");

        final File expImgFile = new File(tempDir, "envifile.img");
        assertEquals(false, expImgFile.exists());
        final File expBinFile = new File(tempDir, "envifile.bin");
        assertEquals(expBinFile, EnviProductReader.createEnviImageFile(hdrFile1));
        assertEquals(expBinFile, EnviProductReader.createEnviImageFile(hdrFile2));
    }
}