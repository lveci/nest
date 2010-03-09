package org.esa.nest.util;

import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.application.ApplicationPage;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.datamodel.Product;
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