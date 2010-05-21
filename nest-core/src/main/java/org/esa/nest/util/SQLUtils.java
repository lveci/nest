package org.esa.nest.util;

import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.datamodel.AbstractMetadata;

import java.sql.Date;
import java.util.Calendar;

/**
 */
public class SQLUtils {

    public static String getOrList(final String columnStr, final String values[]) {
        String orListStr = "(";
        int i=0;
        for(String v : values) {
            if(i>0)
                orListStr += " OR ";
            orListStr += columnStr+" = '"+v+"'";
            ++i;
        }
        orListStr += ")";
        return orListStr;
    }

    public static Date toSQLDate(final ProductData.UTC utc) {
        return toSQLDate(utc.getAsCalendar());
    }

    public static Date toSQLDate(final Calendar cal) {
        return new java.sql.Date(cal.getTimeInMillis());
    }

    public static String[] prependString(final String firstValue, final String[] origList) {
        final String[] newList = new String[origList.length + 1];
        newList[0] = firstValue;
        System.arraycopy(origList, 0, newList, 1, origList.length);
        return newList;
    }

    public static String addAND(String str) {
        if(!str.isEmpty())
            return " AND ";
        return "";
    }
}
