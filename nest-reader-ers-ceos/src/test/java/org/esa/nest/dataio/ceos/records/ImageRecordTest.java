/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dataio.ceos.records;

import junit.framework.TestCase;
import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;
import org.esa.nest.dataio.ceos.CeosTestHelper;

import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageRecordTest extends TestCase {

    private MemoryCacheImageOutputStream _ios = null;
    private String _prefix;
    private BinaryFileReader _reader;

    private final static String mission = "ers";
    private final static String image_recordDefinition = "image_record.xml";

    @Override
    protected void setUp() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream(24);
        _ios = new MemoryCacheImageOutputStream(os);
        _prefix = "ImageRecordTest_prefix";
        _ios.writeBytes(_prefix);
        writeRecordData(_ios);
        _ios.writeBytes("ImageRecordTest_suffix"); // as suffix
        _reader = new BinaryFileReader(_ios);
    }

    public void testInit_SimpleConstructor() throws IOException, IllegalBinaryFormatException {
        _reader.seek(_prefix.length());

        final ImageRecord record = new ImageRecord(_reader, mission, image_recordDefinition);
    }

    private static void writeRecordData(final ImageOutputStream ios) throws IOException {
        BaseRecordTest.writeRecordData(ios);

       // ios.writeInt(1234); // prefixDataLineNumber // B4
        ios.writeInt(2345); // ccdUnitNumber // B4
       // ios.writeInt(3456); // scanStartTimeMillisAtDay // B4
       // ios.writeShort(4567); // scanStartTimeMicros // B2
        CeosTestHelper.writeBlanks(ios, BaseRecordTest.RECORD_LENGTH - 34);
    }


}
