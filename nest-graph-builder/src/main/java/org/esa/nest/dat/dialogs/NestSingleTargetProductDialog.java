/*
 * Copyright (C) 2011 by Array Systems Computing Inc. http://www.array.ca
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

import com.bc.ceres.binding.*;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.PropertyPane;
import com.bc.ceres.swing.selection.AbstractSelectionChangeListener;
import com.bc.ceres.swing.selection.Selection;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.OperatorUI;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.internal.RasterDataNodeValues;
import org.esa.beam.framework.gpf.ui.*;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.visat.VisatApp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class NestSingleTargetProductDialog extends DefaultSingleTargetProductDialog {

    private final OperatorUI opUI;

    public NestSingleTargetProductDialog(String operatorName, AppContext appContext, String title, String helpID) {
        super(operatorName, appContext, title, helpID);

        final OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("operatorName " + operatorName);
        }

        opUI = operatorSpi.createOperatorUI();

        addParameters(operatorSpi, appContext, helpID);

        getJDialog().setMinimumSize(new Dimension(500, 500));
    }

    @Override
    protected void addParametersPane(final OperatorSpi operatorSpi, final AppContext appContext, final String helpID) {
        //ignore
    }

    private void addParameters(final OperatorSpi operatorSpi, final AppContext appContext, final String helpID) {
        //OperatorMenu operatorMenu = new OperatorMenu(this.getJDialog(),
        //                                             operatorSpi.getOperatorClass(),
        //                                             parameterSupport,
        //                                             helpID);
        final PropertySet propertyContainer = parameterSupport.getPopertySet();
        final ArrayList<SourceProductSelector> sourceProductSelectorList = ioParametersPanel.getSourceProductSelectorList();

        sourceProductSelectorList.get(0).addSelectionChangeListener(new AbstractSelectionChangeListener() {

            @Override
            public void selectionChanged(SelectionChangeEvent event) {
                final Product selectedProduct = (Product) event.getSelection().getSelectedValue();
                if(selectedProduct != null) {
                    final TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();
                    targetProductSelectorModel.setProductName(selectedProduct.getName() + getTargetProductNameSuffix());
                    opUI.setSourceProducts(new Product[] { selectedProduct });
                }
            }
        });

        if (propertyContainer.getProperties().length > 0) {
            if (!sourceProductSelectorList.isEmpty()) {
                Property[] properties = propertyContainer.getProperties();
                List<PropertyDescriptor> rdnTypeProperties = new ArrayList<PropertyDescriptor>(properties.length);
                for (Property property : properties) {
                    PropertyDescriptor parameterDescriptor = property.getDescriptor();
                    if (parameterDescriptor.getAttribute(RasterDataNodeValues.ATTRIBUTE_NAME) != null) {
                        rdnTypeProperties.add(parameterDescriptor);
                    }
                }
                rasterDataNodeTypeProperties = rdnTypeProperties.toArray(
                        new PropertyDescriptor[rdnTypeProperties.size()]);
            }

            final JComponent paremetersPanel = opUI.CreateOpTab(operatorName, parameterSupport.getParameterMap(), appContext);

            paremetersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
            this.form.add("Processing Parameters", new JScrollPane(paremetersPanel));

            //getJDialog().setJMenuBar(operatorMenu.createDefaultMenu());
        }
    }

    public void setIcon(final ImageIcon ico) {
        if(ico == null) return;
        this.getJDialog().setIconImage(ico.getImage());
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        if(validateUI()) {
            opUI.updateParameters();

            final HashMap<String, Product> sourceProducts = ioParametersPanel.createSourceProductsMap();
            return GPF.createProduct(operatorName, parameterSupport.getParameterMap(), sourceProducts);
        }
        return null;
    }

    private boolean validateUI() {
        final UIValidation validation = opUI.validateParameters();
        if(validation.getState() == UIValidation.State.WARNING) {
            final String msg = "Warning: "+validation.getMsg()+
                    "\n\nWould you like to continue?";
            return VisatApp.getApp().showQuestionDialog(msg, null) == 0;
        } else if(validation.getState() == UIValidation.State.ERROR) {
            final String msg = "Error: "+validation.getMsg();
            VisatApp.getApp().showErrorDialog(msg);
            return false;
        }
        return true;
    }
}