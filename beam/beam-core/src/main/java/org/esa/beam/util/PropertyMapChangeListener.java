/*
 * $Id: PropertyMapChangeListener.java,v 1.1 2009-04-28 14:39:33 lveci Exp $
 *
 * Copyright (C) 2002 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package org.esa.beam.util;

import java.util.EventListener;

/**
 * This type of listener is informed if some property within the property map changes its value.
 *
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2009-04-28 14:39:33 $
 */
public interface PropertyMapChangeListener extends EventListener {

    /**
     * Called if the property map changed.
     */
    void propertyMapChanged(PropertyMap propertyMap);
}
