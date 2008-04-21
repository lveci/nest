
package org.esa.nest.dat;

import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.VisatMain;
import org.esa.beam.framework.ui.application.ApplicationDescriptor;

public class DatMain extends VisatMain {
    @Override
    protected VisatApp createApplication(ApplicationDescriptor applicationDescriptor) {
        return new DatApp(applicationDescriptor);
    }
}