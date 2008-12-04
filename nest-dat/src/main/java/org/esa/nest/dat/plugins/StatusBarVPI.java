package org.esa.nest.dat.plugins;

import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;

import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.ui.PixelPositionListener;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.util.PropertyMap;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.AbstractVisatPlugIn;

import com.bc.ceres.glayer.support.ImageLayer;
import com.jidesoft.status.LabelStatusBarItem;

public class StatusBarVPI extends AbstractVisatPlugIn {

    private VisatApp _visatApp;
    private LabelStatusBarItem _dimensionStatusBarItem;
    private HashMap<Product, PixelPositionListener> _pixelPosListeners;
    private float _pixelOffsetX;
    private float _pixelOffsetY;
    private boolean _showPixelOffsetDecimals;

    public void start(final VisatApp visatApp) {
        _visatApp = visatApp;
        final PropertyMap preferences = visatApp.getPreferences();
        preferences.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                final String propertyName = evt.getPropertyName();
                if (VisatApp.PROPERTY_KEY_PIXEL_OFFSET_FOR_DISPLAY_X.equals(propertyName)) {
                    setPixelOffsetX(preferences);
                } else if (VisatApp.PROPERTY_KEY_PIXEL_OFFSET_FOR_DISPLAY_Y.equals(propertyName)) {
                    setPixelOffsetY(preferences);
                } else if (VisatApp.PROPERTY_KEY_PIXEL_OFFSET_FOR_DISPLAY_SHOW_DECIMALS.equals(propertyName)) {
                    setShowPixelOffsetDecimals(preferences);
                }
            }
        });
        setPixelOffsetX(preferences);
        setPixelOffsetY(preferences);
        setShowPixelOffsetDecimals(preferences);

        visatApp.addInternalFrameListener(new InternalFrameAdapter() {

            @Override
            public void internalFrameOpened(InternalFrameEvent e) {
                final Container contentPane = e.getInternalFrame().getContentPane();
                if (contentPane instanceof ProductSceneView) {
                    final ProductSceneView view = (ProductSceneView) contentPane;
                    view.addPixelPositionListener(registerPixelPositionListener(view.getProduct()));
                }
            }

            @Override
            public void internalFrameClosing(InternalFrameEvent e) {
                final Container contentPane = e.getInternalFrame().getContentPane();
                if (contentPane instanceof ProductSceneView) {
                    final ProductSceneView view = (ProductSceneView) contentPane;
                    view.removePixelPositionListener(getPixelPositionListener(view.getProduct()));
                }
            }
        });
    }

    private void setPixelOffsetY(final PropertyMap preferences) {
        _pixelOffsetY = (float) preferences.getPropertyDouble(VisatApp.PROPERTY_KEY_PIXEL_OFFSET_FOR_DISPLAY_Y,
                                                              VisatApp.PROPERTY_DEFAULT_PIXEL_OFFSET_FOR_DISPLAY);
    }

    private void setPixelOffsetX(final PropertyMap preferences) {
        _pixelOffsetX = (float) preferences.getPropertyDouble(VisatApp.PROPERTY_KEY_PIXEL_OFFSET_FOR_DISPLAY_X,
                                                              VisatApp.PROPERTY_DEFAULT_PIXEL_OFFSET_FOR_DISPLAY);
    }

    private void setShowPixelOffsetDecimals(final PropertyMap preferences) {
        _showPixelOffsetDecimals = preferences.getPropertyBool(
                VisatApp.PROPERTY_KEY_PIXEL_OFFSET_FOR_DISPLAY_SHOW_DECIMALS,
                VisatApp.PROPERTY_DEFAULT_PIXEL_OFFSET_FOR_DISPLAY_SHOW_DECIMALS);
    }

    private PixelPositionListener registerPixelPositionListener(Product product) {
        if (_pixelPosListeners == null) {
            _pixelPosListeners = new HashMap<Product, PixelPositionListener>();
        }
        PixelPositionListener listener = new PixelPosHandler(product.getProductRefString());
        _pixelPosListeners.put(product, listener);
        return listener;
    }

    private PixelPositionListener getPixelPositionListener(Product product) {
        if (_pixelPosListeners != null) {
            return _pixelPosListeners.get(product);
        }
        return null;
    }

    private LabelStatusBarItem getDimensionsStatusBarItem() {
        if (_dimensionStatusBarItem == null) {
            _dimensionStatusBarItem = (LabelStatusBarItem) _visatApp.getStatusBar().getItemByName("STATUS_BAR_DIMENSIONS_ITEM");
        }
        return _dimensionStatusBarItem;
    }

    private class PixelPosHandler implements PixelPositionListener {

        private final String _refString;
        private StringBuilder _text;
        private final String _EMPTYSTR = "";

        public PixelPosHandler(String refString) {
            _refString = refString;
            _text = new StringBuilder(64);
        }

        public void pixelPosChanged(ImageLayer imageLayer,
                                    int pixelX,
                                    int pixelY,
                                    int currentLevel,
                                    boolean pixelPosValid,
                                    MouseEvent e) {
            LabelStatusBarItem dimensionStatusBarItem = getDimensionsStatusBarItem();
            if (dimensionStatusBarItem == null || !dimensionStatusBarItem.isVisible()) {
                return;
            }
            if (pixelPosValid) {
                _text.setLength(0);

                Product prod = _visatApp.getSelectedProductSceneView().getProduct();
                int width = prod.getSceneRasterWidth();
                int height = prod.getSceneRasterHeight();
                _text.append(width).append(" x ").append(height);

                dimensionStatusBarItem.setText(_text.toString());
            } else {
                dimensionStatusBarItem.setText(_EMPTYSTR);
            }
        }

        public void pixelPosNotAvailable() {
            LabelStatusBarItem positionStatusBarItem = getDimensionsStatusBarItem();
            if (positionStatusBarItem != null) {
                positionStatusBarItem.setText(_EMPTYSTR);
            }
        }
    }
}