package org.jdoris.nest.dat.dialogs;

import org.esa.nest.dat.dialogs.BaseFileModel;
import org.esa.nest.dat.dialogs.FileTableModel;
import org.esa.nest.db.ProductEntry;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.gpf.OperatorUtils;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.dataio.ProductIO;

import java.io.File;
import java.io.IOException;

public class InSARFileModel extends BaseFileModel implements FileTableModel {

        protected void setColumnData() {
            titles = new String[]{
                "File Name", "Mst/Slv", "Acquisition", "Track", "Orbit",
                "Coherence", "pBaseline", "tBaseline"
            };

            types = new Class[]{
                String.class, String.class, String.class, String.class, String.class,
                String.class, String.class, String.class
            };

            widths = new int[]{
                35, 3, 20, 3, 3,
                 5, 5, 5
            };
        }

        protected TableData createFileStats(final File file) {
            return new TableData(file);
        }

        protected TableData createFileStats(final ProductEntry entry) {
            return new TableData(entry);
        }
    }