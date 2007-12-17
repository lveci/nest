
package org.esa.nest.dat;

import org.esa.beam.framework.ui.tool.AbstractTool;

public abstract class DatTool extends AbstractTool {

    private final DatApp _datApp;

    public DatTool(DatApp datApp) {
        _datApp = datApp;
    }

    public DatApp getDatApp() {
        return _datApp;
    }
}
