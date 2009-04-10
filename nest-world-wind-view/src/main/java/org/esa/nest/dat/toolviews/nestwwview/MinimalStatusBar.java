package org.esa.nest.dat.toolviews.nestwwview;

import gov.nasa.worldwind.util.StatusBar;

/**
 * NEST
 * User: lveci
 * Date: Apr 9, 2009
 */
public class MinimalStatusBar extends StatusBar {

    MinimalStatusBar() {
        super();

        this.remove(altDisplay);
    }
}
