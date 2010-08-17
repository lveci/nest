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
package org.esa.nest.dat.plugins;

import com.bc.ceres.glayer.support.ImageLayer;
import com.jidesoft.status.LabelStatusBarItem;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductNode;
import org.esa.beam.framework.ui.PixelPositionListener;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.visat.AbstractVisatPlugIn;
import org.esa.beam.visat.VisatApp;

import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;

public class StatusBarVPI extends AbstractVisatPlugIn {

    private VisatApp _visatApp;
    private LabelStatusBarItem _dimensionStatusBarItem;
    private LabelStatusBarItem _valueStatusBarItem;
    private HashMap<Product, PixelPositionListener> _pixelPosListeners;

    public void start(final VisatApp visatApp) {
        _visatApp = visatApp;

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

    private LabelStatusBarItem getValueStatusBarItem() {
        if (_valueStatusBarItem == null) {
            _valueStatusBarItem = (LabelStatusBarItem) _visatApp.getStatusBar().getItemByName("STATUS_BAR_VALUE_ITEM");
        }
        return _valueStatusBarItem;
    }

    private class PixelPosHandler implements PixelPositionListener {

        private final StringBuilder _text;
        private final String _EMPTYSTR = "";

        public PixelPosHandler(String refString) {
            _text = new StringBuilder(64);
        }

        public void pixelPosChanged(ImageLayer imageLayer,
                                    int pixelX,
                                    int pixelY,
                                    int currentLevel,
                                    boolean pixelPosValid,
                                    MouseEvent e) {
            final LabelStatusBarItem dimensionStatusBarItem = getDimensionsStatusBarItem();
            final LabelStatusBarItem valueStatusBarItem = getValueStatusBarItem();
            if (dimensionStatusBarItem == null || valueStatusBarItem == null) {
                return;
            }
            if (pixelPosValid) {
                _text.setLength(0);

                final Product prod = _visatApp.getSelectedProductSceneView().getProduct();
                final int width = prod.getSceneRasterWidth();
                final int height = prod.getSceneRasterHeight();
                _text.append(width).append(" x ").append(height);

                dimensionStatusBarItem.setText(_text.toString());

                final ProductNode prodNode = _visatApp.getSelectedProductNode();
                if(prodNode != null) {
                    final String selectedNodeName = prodNode.getName();
                    final Band band = prod.getBand(selectedNodeName);
                    if(band != null)
                        valueStatusBarItem.setText(band.getPixelString(pixelX, pixelY));
                }
            } else {
                dimensionStatusBarItem.setText(_EMPTYSTR);
                valueStatusBarItem.setText(_EMPTYSTR);
            }
        }

        public void pixelPosNotAvailable() {
            final LabelStatusBarItem dimensionStatusBarItem = getDimensionsStatusBarItem();
            if (dimensionStatusBarItem != null) {
                dimensionStatusBarItem.setText(_EMPTYSTR);
            }
            final LabelStatusBarItem valueStatusBarItem = getValueStatusBarItem();
            if (valueStatusBarItem != null) {
                valueStatusBarItem.setText(_EMPTYSTR);
            }
        }
    }
}