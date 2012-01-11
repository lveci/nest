/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
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
package org.esa.beam;

import com.bc.ceres.core.CoreException;
import com.bc.ceres.core.runtime.Activator;
import com.bc.ceres.core.runtime.ModuleContext;
import org.esa.beam.framework.gpf.OperatorUI;

/**
 * <p><i><b>IMPORTANT NOTE:</b>
 * This class does not belong to the public API.
 * It is not intended to be used by clients.
 * It is invoked by the {@link com.bc.ceres.core.runtime.ModuleRuntime ceres runtime} to activate the {@code beam-core} module.</i>
 *
 * @author Marco Peters

 */
public class GPFActivator implements Activator {

    private static ModuleContext moduleContext;

    public static boolean isStarted() {
        return moduleContext != null;
    }

    @Override
    public void start(ModuleContext moduleContext) throws CoreException {
        GPFActivator.moduleContext = moduleContext;
        registerOperatorUIs(moduleContext);
    }

    @Override
    public void stop(ModuleContext moduleContext) throws CoreException {
        GPFActivator.moduleContext = null;
    }

    private static void registerOperatorUIs(ModuleContext moduleContext) {
        //BeamCoreActivator.loadExecutableExtensions(moduleContext,
        //                                           "operatorUIs",
        //                                           "operatorUI",
        //                                           OperatorUI.class);
    }
}