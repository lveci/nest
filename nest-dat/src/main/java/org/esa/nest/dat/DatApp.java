
package org.esa.nest.dat;

import com.bc.ceres.core.Assert;
import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.core.runtime.Module;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import com.bc.layer.LayerModel;
import com.bc.swing.desktop.TabbedDesktopPane;
import com.jidesoft.action.CommandBar;
import com.jidesoft.action.CommandMenuBar;
import com.jidesoft.action.DockableBarContext;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.ui.*;
import org.esa.beam.framework.ui.application.ApplicationPage;
import org.esa.beam.framework.ui.application.ApplicationWindow;
import org.esa.beam.framework.ui.application.ToolViewDescriptor;
import org.esa.beam.framework.ui.application.ApplicationDescriptor;
import org.esa.beam.framework.ui.command.Command;
import org.esa.beam.framework.ui.product.*;
import org.esa.beam.framework.ui.tool.ToolButtonFactory;
import org.esa.beam.util.*;
import org.esa.beam.visat.*;

import javax.swing.*;
import javax.swing.event.InternalFrameListener;
import java.awt.*;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URL;

public final class DatApp extends VisatApp {
    public DatApp(ApplicationDescriptor applicationDescriptor) {
        super(applicationDescriptor);
    }

    public static DatApp getApp() {
        return (DatApp) VisatApp.getApp();
    }

    // You can now override numerous createXXX() methods
    // to customize the application GUI

    protected ModalDialog createAboutBox() {
        return new DatAboutBox();
    }

}