package org.esa.nest.dataio.ceos.records;

import org.esa.nest.dataio.BinaryFileReader;
import org.esa.nest.dataio.IllegalBinaryFormatException;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;

public class BaseSceneHeaderRecord extends BaseRecord {

    private static final int YEAR_OFFSET = 2000;
    private static final HashMap MONTH_TABLE = new HashMap(12);

    static {
        MONTH_TABLE.put("Jan", 1);
        MONTH_TABLE.put("Feb", 2);
        MONTH_TABLE.put("Mar", 3);
        MONTH_TABLE.put("Apr", 4);
        MONTH_TABLE.put("May", 5);
        MONTH_TABLE.put("Jun", 6);
        MONTH_TABLE.put("Jul", 7);
        MONTH_TABLE.put("Aug", 8);
        MONTH_TABLE.put("Sep", 9);
        MONTH_TABLE.put("Oct", 10);
        MONTH_TABLE.put("Nov", 11);
        MONTH_TABLE.put("Dec", 12);
    }

    public BaseSceneHeaderRecord(final BinaryFileReader reader, final long startPos,
                                 String mission, String definitionFile)
            throws IOException, IllegalBinaryFormatException {
        super(reader, startPos, mission, definitionFile);
        //readGeneralFields(reader);
        //reader.seek(getAbsolutPosition(getRecordLength()));
    }

    public Calendar getDateImageWasTaken() {
        final Calendar calendar = Calendar.getInstance();
        String dateImageWasTaken = getAttributeString("Scene centre time");
        final int days = Integer.parseInt(dateImageWasTaken.substring(0, 2));
        final int month = (Integer) MONTH_TABLE.get(dateImageWasTaken.substring(2, 5));
        final int year = Integer.parseInt(dateImageWasTaken.substring(5, 7)) + YEAR_OFFSET;

        calendar.set(year, month - 1, days, 0, 0, 0);
        return calendar;
    }

}
