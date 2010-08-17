/*
 * Copyright (C) 2010 Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.nest.dat.dialogs;

import org.esa.beam.framework.ui.GridBagUtils;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jun 5, 2008
 * To change this template use File | Settings | File Templates.
 */
public class ProductSelectorDialog extends ModalDialog {

    private final JList list;
    private boolean ok = false;

    public ProductSelectorDialog(String title, String[] productNames) {
        super(VisatApp.getApp().getMainFrame(), title, ModalDialog.ID_OK_CANCEL, null);

        final JPanel content = GridBagUtils.createPanel();
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets.right = 4;
        gbc.gridy = 0;
        gbc.weightx = 0;

        gbc.insets.top = 2;
        list = new JList(productNames);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        content.add(list);

        getJDialog().setMinimumSize(new Dimension(400, 100));

        setContent(content);
    }

    public String getSelectedProductName() {
        Object selection = list.getSelectedValue();
        if(selection == null) {
            if(list.getModel().getSize() > 0) {
                selection = list.getModel().getElementAt(0);
            }
        }
        return (String)selection;
    }

    protected void onOK() {
        ok = true;
        hide();
    }

    public boolean IsOK() {
        return ok;
    }

}