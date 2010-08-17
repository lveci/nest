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
package org.esa.nest.util;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.application.ApplicationPage;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.util.PropertyMap;

import javax.swing.*;
import java.awt.*;

/**
 * Mock AppContext for tests
 */
public class MockAppContext implements AppContext {
    private final PropertyMap preferences = new PropertyMap();
    private final ProductManager prodMan = new ProductManager();
    private Product selectedProduct = null;

    public Window getApplicationWindow() {
        return null;
    }

    public String getApplicationName() {
        return "Killer App";
    }

    public ApplicationPage getApplicationPage() {
        return null;
    }

    public Product getSelectedProduct() {
        return selectedProduct;
    }

    public void setSelectedProduct(Product prod) {
        selectedProduct = prod;
        prodMan.addProduct(selectedProduct);
    }

    public void handleError(Throwable e) {
        JOptionPane.showMessageDialog(getApplicationWindow(), e.getMessage());
    }

    public void handleError(String message, Throwable e) {
        JOptionPane.showMessageDialog(getApplicationWindow(), message);
    }

    public PropertyMap getPreferences() {
        return preferences;
    }

    public ProductManager getProductManager() {
        return prodMan;
    }

    public ProductSceneView getSelectedProductSceneView() {
        return null;
    }
}