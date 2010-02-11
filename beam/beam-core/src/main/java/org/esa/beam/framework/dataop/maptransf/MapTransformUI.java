/*
 * $Id: MapTransformUI.java,v 1.2 2010-02-11 17:02:24 lveci Exp $
 *
 * Copyright (c) 2003 Brockmann Consult GmbH. All right reserved.
 * http://www.brockmann-consult.de
 */
package org.esa.beam.framework.dataop.maptransf;

import java.awt.Component;

/**
 * @deprecated since BEAM 4.7, replaced by GPF operator 'Reproject'
 */
@Deprecated
public interface MapTransformUI {

    MapTransform createTransform();

    void resetToDefaults();

    boolean verifyUserInput();

    Component getUIComponent();
}
