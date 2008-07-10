package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.gpf.ui.StackReaderOpUI;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jun 5, 2008
 * To change this template use File | Settings | File Templates.
 */
public class StackDialog extends ModalDialog {

    StackReaderOpUI.FileModel fileModel = new StackReaderOpUI.FileModel();
    private boolean ok = false;

    public StackDialog(String title, String label, String defaultValue) {
        super(VisatApp.getApp().getMainFrame(), title, ModalDialog.ID_OK_CANCEL, null);

        JComponent content =  StackReaderOpUI.createComponent(fileModel);

        setContent(content);
    }

    protected void onOK() {
        ok = true;
        hide();
    }

    public boolean IsOK() {
        return ok;
    }

}