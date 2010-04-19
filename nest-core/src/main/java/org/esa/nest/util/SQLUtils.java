package org.esa.nest.util;

import org.esa.nest.datamodel.AbstractMetadata;

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

    public static String[] prependString(final String firstValue, final String[] origList) {
        final String[] newList = new String[origList.length + 1];
        newList[0] = firstValue;
        System.arraycopy(origList, 0, newList, 1, origList.length);
        return newList;
    }
}
