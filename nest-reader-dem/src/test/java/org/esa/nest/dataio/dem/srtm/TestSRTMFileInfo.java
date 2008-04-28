package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;

import java.text.ParseException;


public class TestSRTMFileInfo extends TestCase {

    protected void setUp() throws Exception {
    }

    protected void tearDown() throws Exception {
    }


    public void testValidFileSize() {
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (1L * 1L)));
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (2L * 2L)));
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (3L * 3L)));
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (4L * 4L)));
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (5L * 5L)));
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (1001L * 1001L)));
        assertTrue(SRTMFileInfo.isValidFileSize(2 * (6844L * 6844L)));

        assertFalse(SRTMFileInfo.isValidFileSize(-2));
        assertFalse(SRTMFileInfo.isValidFileSize(-1));
        assertFalse(SRTMFileInfo.isValidFileSize(0));
        assertFalse(SRTMFileInfo.isValidFileSize(1));
        assertFalse(SRTMFileInfo.isValidFileSize(3));
        assertFalse(SRTMFileInfo.isValidFileSize(4));
        assertFalse(SRTMFileInfo.isValidFileSize(5));
        assertFalse(SRTMFileInfo.isValidFileSize(6));
        assertFalse(SRTMFileInfo.isValidFileSize(7));
        assertFalse(SRTMFileInfo.isValidFileSize(9));
        assertFalse(SRTMFileInfo.isValidFileSize(87565));
        assertFalse(SRTMFileInfo.isValidFileSize(-76));
    }

    public void testExtractEastingNorthingWithValidStrings() throws ParseException {
        int[] values = SRTMFileInfo.parseEastingNorthing("W15N00.DEM");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals(-15, values[0]);
        assertEquals(00, values[1]);

        values = SRTMFileInfo.parseEastingNorthing("E135S75.DEM");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals(135, values[0]);
        assertEquals(-75, values[1]);
    }

    public void testExtractEastingNorthingWithInvalidStrings() {
        try {
            SRTMFileInfo.parseEastingNorthing("w020n10");  // string length  = 7
            fail("ParseException expected because the string not ends with '\\..+' ");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(7, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing(null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf("null") > -1);
        } catch (ParseException e) {
            fail("IllegalArgumentException expected");
        }

        try {
            SRTMFileInfo.parseEastingNorthing("");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().indexOf("empty") > -1);
        } catch (ParseException e) {
            fail("IllegalArgumentException expected");
        }

        try {
            SRTMFileInfo.parseEastingNorthing("aw010n20");
            fail("ParseException expected because illegal 'a' character");
        } catch (ParseException expected) {
            assertEquals("Illegal direction character.", expected.getMessage());
            assertEquals(0, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("w10n100.SRTM");
            fail("ParseException expected because the value for north direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '100' for north direction is out of the range 0 ... 90.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("e190n80.SRTM");
            fail("ParseException expected because the value for east direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '190' for east direction is out of the range 0 ... 180.", expected.getMessage());
            assertEquals(3, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("w190s80.SRTM");
            fail("ParseException expected because the value for west direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '-190' for west direction is out of the range -180 ... 0.", expected.getMessage());
            assertEquals(3, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("s80s80.SRTM");
            fail("ParseException expected because value for easting is not available");
        } catch (ParseException expected) {
            assertEquals("Easting value not available.", expected.getMessage());
            assertEquals(-1, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("e80e80.SRTM");
            fail("ParseException expected because value for northing is not available");
        } catch (ParseException expected) {
            assertEquals("Northing value not available.", expected.getMessage());
            assertEquals(-1, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("e80s80SRTM");
            fail("ParseException expected because northing easting values are not followed by a dot");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("e80s80.");
            fail("ParseException expected because the dot is not followed by at least one character");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }
    }
}
