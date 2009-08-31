package org.esa.nest.gpf;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Dec 3, 2008
 * Time: 1:05:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class OperatorUIUtils {


    public static void initBandList(JList bandList, String[] bandNames) {
        final Object selectedValues[] = bandList.getSelectedValues();

        bandList.removeAll();
        bandList.setListData(bandNames);
        bandList.setFixedCellWidth(200);
        bandList.setMinimumSize(new Dimension(50, 4));
        
        final int size = bandList.getModel().getSize();
        final ArrayList<Integer> indeces = new ArrayList<Integer>(size);

        for (Object selectedValue : selectedValues) {
            final String selValue = (String) selectedValue;

            for (int j = 0; j < size; ++j) {
                final String val = (String) bandList.getModel().getElementAt(j);
                if (val.equals(selValue)) {
                    indeces.add(j);
                    break;
                }
            }
        }
        final int[] selIndex = new int[indeces.size()];
        for(int i=0; i < indeces.size(); ++i) {
            selIndex[i] = indeces.get(i);
        }
        bandList.setSelectedIndices(selIndex);
    }

    public static void updateBandList(JList bandList, Map<String, Object> paramMap) {
        final Object selectedValues[] = bandList.getSelectedValues();
        final String bandNames[] = new String[selectedValues.length];
        for(int i=0; i<selectedValues.length; ++i) {
            bandNames[i] = (String)selectedValues[i];
        }

        paramMap.put("sourceBandNames", bandNames);
    }

    public static double getNoDataValue(File extFile) {
        try {
            final ProductReader productReader = ProductIO.getProductReaderForFile(extFile);
            final Product product = productReader.readProductNodes(extFile, null);
            return product.getBandAt(0).getNoDataValue();
        } catch(Exception e) {
            //
        }
        return 0;
    }
}