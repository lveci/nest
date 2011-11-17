package org.esa.nest.db;

import org.esa.nest.util.ResourceUtils;

import javax.swing.*;
import java.io.File;

/**
 * Caches symbols
 */
public class SymbolLibrary {

    public final static File symbolFolder = new File(ResourceUtils.getResFolder(), "symbols");

    private static SymbolLibrary _instance = null;

    public static SymbolLibrary instance() {
        if(_instance == null) {
            _instance = new SymbolLibrary();
        }
        return _instance;
    }

    public ImageIcon loadIcon() {
        return new ImageIcon(ResourceUtils.loadImage(new File(symbolFolder, "flag_blue.png")));
    }
}
