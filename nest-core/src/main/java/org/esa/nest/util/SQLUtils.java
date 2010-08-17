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
package org.esa.nest.util;

import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.StringUtils;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.db.ProductDB;

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

    public static String insertTableName(final String[] tokens, final String tableName, final String freeQuery) {
        String query = freeQuery;
        for(String tok : tokens) {
            query = query.replaceAll(tok, tableName+'.'+tok);
        }
        return query;
    }
}
