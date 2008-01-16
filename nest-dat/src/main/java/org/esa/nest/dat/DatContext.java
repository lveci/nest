package org.esa.nest.dat;

import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.visat.SharedApp;
import org.esa.beam.util.PropertyMap;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 16, 2008
 * Time: 10:13:46 AM
 * To change this template use File | Settings | File Templates.
 */
public class DatContext implements AppContext {
        private String toolTitle;

        public DatContext(String toolTitle) {
            this.toolTitle = toolTitle;
        }

        public Product[] getProducts() {
            return SharedApp.instance().getApp().getProductManager().getProducts();
        }

        public Product getSelectedProduct() {
            return SharedApp.instance().getApp().getSelectedProduct();
        }

        public Window getApplicationWindow() {
            return SharedApp.instance().getApp().getMainFrame();
        }

        public String getApplicationName() {
            return SharedApp.instance().getApp().getAppName();
        }

        public void addProduct(Product product) {
            SharedApp.instance().getApp().addProduct(product);
        }

        public void handleError(Throwable e) {
            SharedApp.instance().getApp().showErrorDialog(toolTitle, e.getMessage());
        }

        public PropertyMap getPreferences() {
            return SharedApp.instance().getApp().getPreferences();
        }
}
