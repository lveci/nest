package com.bc.ceres.core;

/**
 * Objects implementing this interface can be dynamically extended.
 *
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2009-04-09 17:06:19 $
 */
public interface Extensible {
    /**
     * Gets the extension for this object corresponding to a specified extension
     * type.
     *
     * @param extensionType the extension type.
     * @return the extension for this object corresponding to the specified type.
     */
    <E> E getExtension(Class<E> extensionType);
}
