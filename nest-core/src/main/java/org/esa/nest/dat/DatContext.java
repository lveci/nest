package org.esa.nest.dat;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductManager;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.application.ApplicationPage;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.visat.VisatApp;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Jan 16, 2008
 * Time: 10:13:46 AM
 * To change this template use File | Settings | File Templates.
 */
public class DatContext implements AppContext {
        private final String toolTitle;

        public DatContext(String toolTitle) {
            this.toolTitle = toolTitle;
        }

        public ProductManager getProductManager() {
            return VisatApp.getApp().getProductManager();
        }

        public Product getSelectedProduct() {
            return VisatApp.getApp().getSelectedProduct();
        }

        public Window getApplicationWindow() {
            return VisatApp.getApp().getMainFrame();
        }

        public ApplicationPage getApplicationPage() {
            return VisatApp.getApp().getApplicationPage();
        }

        public String getApplicationName() {
            return VisatApp.getApp().getAppName();
        }

		public void handleError(Throwable e) {
            VisatApp.getApp().showErrorDialog(toolTitle, e.getMessage());
        }
		
        public void handleError(String message, Throwable e) {
            VisatApp.getApp().showErrorDialog(toolTitle, message);
        }

        public PropertyMap getPreferences() {
            return VisatApp.getApp().getPreferences();
        }
		
		public ProductSceneView getSelectedProductSceneView() {
			return VisatApp.getApp().getSelectedProductSceneView();
		}
}
