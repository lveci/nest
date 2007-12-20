package org.esa.nest.dataio.dem.srtm;

import junit.framework.TestCase;

import java.text.ParseException;


public class SRTMFileInfoTest extends TestCase {

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
        int[] values = SRTMFileInfo.parseEastingNorthing("00N015W.SRTM");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals(-15, values[0]);
        assertEquals(00, values[1]);

        values = SRTMFileInfo.parseEastingNorthing("75S135E.SRTM");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals(135, values[0]);
        assertEquals(-75, values[1]);
    }

    public void testExtractEastingNorthingWithInvalidStrings() {
        try {
            SRTMFileInfo.parseEastingNorthing("020n10w");  // string length  = 7
            fail("ParseException expected because the string not ends with '\\..+' ");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(7, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("005n104w"); // string length  = 8
            fail("ParseException expected because the string not ends with '\\..+' ");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(8, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("05S104E");
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
            SRTMFileInfo.parseEastingNorthing("020n10aw");
            fail("ParseException expected because illegal 'a' character");
        } catch (ParseException expected) {
            assertEquals("Illegal direction character.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("w0a0n10.SRTM");
            fail("ParseException expected because the string starts with no digit.");
        } catch (ParseException expected) {
            assertEquals("Digit character expected.", expected.getMessage());
            assertEquals(0, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("100n10w.SRTM");
            fail("ParseException expected because the value for north direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '100' for north direction is out of the range 0 ... 90.", expected.getMessage());
            assertEquals(3, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("100s10w.SRTM");
            fail("ParseException expected because the value for south direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '-100' for south direction is out of the range -90 ... 0.", expected.getMessage());
            assertEquals(3, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("80n190e.SRTM");
            fail("ParseException expected because the value for east direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '190' for east direction is out of the range 0 ... 180.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("80s190w.SRTM");
            fail("ParseException expected because the value for west direction is out of bounds.");
        } catch (ParseException expected) {
            assertEquals("The value '-190' for west direction is out of the range -180 ... 0.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("80s80s.SRTM");
            fail("ParseException expected because value for easting is not available");
        } catch (ParseException expected) {
            assertEquals("Easting value not available.", expected.getMessage());
            assertEquals(-1, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("80e80e.SRTM");
            fail("ParseException expected because value for northing is not available");
        } catch (ParseException expected) {
            assertEquals("Northing value not available.", expected.getMessage());
            assertEquals(-1, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("80e80sSRTM");
            fail("ParseException expected because northing easting values are not followed by a dot");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }

        try {
            SRTMFileInfo.parseEastingNorthing("80e80s.");
            fail("ParseException expected because the dot is not followed by at least one character");
        } catch (ParseException expected) {
            assertEquals("Illegal string format.", expected.getMessage());
            assertEquals(6, expected.getErrorOffset());
        }
    }
}
