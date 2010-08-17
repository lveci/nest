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
package org.esa.nest.dataio.ceos.radarsat;

import org.esa.nest.dataio.ceos.CEOSConstants;

/**
 * Several constants used for reading Radarsat products.
 */
public class RadarsatConstants implements CEOSConstants {

    private final String[] FORMAT_NAMES = new String[]{"Radarsat 1 CEOS"};
    private final static String[] FORMAT_FILE_EXTENSIONS = new String[]{""};
    private static final String PLUGIN_DESCRIPTION = "Radarsat 1 CEOS Products";      /*I18N*/
    final static String PRODUCT_TYPE_PREFIX = "";
    private static final String VOLUME_FILE_PREFIX = "VDF";

    private final static String MISSION = "radarsat";

    final static String PRODUCT_DESCRIPTION_PREFIX = "Radarsat product ";

    final static String SUMMARY_FILE_NAME = "summary.txt";
    final static String SCENE_LABEL_FILE_NAME = "scene01.lbl";

    private final static int MINIMUM_FILES = 4;    // 4 image files + leader file + volume file + trailer file

    public String getVolumeFilePrefix() {
        return VOLUME_FILE_PREFIX;
    }

    public int getMinimumNumFiles() {
        return MINIMUM_FILES;
    }

    public String getPluginDescription() {
        return PLUGIN_DESCRIPTION;
    }

    public String[] getFormatNames() {
        return FORMAT_NAMES;
    }

    public String[] getForamtFileExtensions() {
        return FORMAT_FILE_EXTENSIONS;
    }

    public String getMission() {
        return MISSION;
    }
}