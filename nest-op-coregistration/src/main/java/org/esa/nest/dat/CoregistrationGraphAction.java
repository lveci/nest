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
package org.esa.nest.dat;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;
import org.esa.nest.dat.plugins.graphbuilder.GraphBuilderDialog;

import java.io.File;


/**
 * Coregistration Graph action.
 *
 */
public class CoregistrationGraphAction extends AbstractVisatAction {

    @Override
    public void actionPerformed(CommandEvent event) {

        final GraphBuilderDialog dialog = new GraphBuilderDialog(getAppContext(), "Coregistration", getHelpId(), false);
        dialog.show();

        final File graphPath = GraphBuilderDialog.getInternalGraphFolder();
        final File graphFile =  new File(graphPath, "CoregistrationGraph.xml");

        dialog.LoadGraph(graphFile);
    }
}