/*
 * $Id: MapTransformUI.java,v 1.1 2009-04-28 14:39:33 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package org.esa.beam.framework.dataop.maptransf;

import java.awt.Component;

public interface MapTransformUI {

    MapTransform createTransform();

    void resetToDefaults();

    boolean verifyUserInput();

    Component getUIComponent();
}
