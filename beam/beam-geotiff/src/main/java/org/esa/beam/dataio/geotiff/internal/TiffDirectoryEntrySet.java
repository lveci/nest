package org.esa.beam.dataio.geotiff.internal;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A directroy entry set implementation for the GeoTIFF format.
 *
 * @author Marco Peters
 * @author Sabine Embacher
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:37:14 $
 */
class TiffDirectoryEntrySet {

    private final SortedMap entryMap;

    public TiffDirectoryEntrySet() {
        entryMap = new TreeMap();
    }

    public void set(final TiffDirectoryEntry entry) {
        final Integer key = getKey(entry);
        entryMap.put(key, entry);
    }

    public TiffDirectoryEntry[] getEntries() {
        return (TiffDirectoryEntry[]) entryMap.values().toArray(new TiffDirectoryEntry[entryMap.size()]);
    }

    public TiffDirectoryEntry getEntry(final TiffShort tag) {
        final Integer key = getKey(tag);
        return (TiffDirectoryEntry) entryMap.get(key);
    }

    private static Integer getKey(final TiffDirectoryEntry entry) {
        return getKey(entry.getTag());
    }

    private static Integer getKey(final TiffShort tag) {
        return tag.getValue();
    }
}
