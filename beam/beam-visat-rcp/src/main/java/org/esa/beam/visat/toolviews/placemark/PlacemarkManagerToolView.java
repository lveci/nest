package org.esa.beam.visat.toolviews.placemark;

import com.bc.ceres.glayer.support.ImageLayer;
import com.jidesoft.grid.SortableTable;
import org.esa.beam.dataio.dimap.DimapProductConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.help.HelpSys;
import org.esa.beam.framework.ui.ModalDialog;
import org.esa.beam.framework.ui.application.support.AbstractToolView;
import org.esa.beam.framework.ui.command.CommandManager;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.beam.framework.ui.product.BandChooser;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.framework.ui.product.ProductTreeListener;
import org.esa.beam.util.*;
import org.esa.beam.util.io.BeamFileChooser;
import org.esa.beam.util.io.BeamFileFilter;
import org.esa.beam.util.io.FileUtils;
import org.esa.beam.visat.VisatApp;
import org.esa.beam.visat.dialogs.PromptDialog;
import org.esa.beam.visat.toolviews.placemark.PlacemarkDialog;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.DOMBuilder;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * A dialog used to manage the list of pins or ground control points associated
 * with a selected product.
 */
public class PlacemarkManagerToolView extends AbstractToolView {

    public static final String PROPERTY_KEY_IO_DIR = "pin.io.dir";
    public static final String NAME_COL_NAME = "Name";
    public static final String LABEL_COL_NAME = "Label";
    public static final String DESC_COL_NAME = "Desc";
    public static final String X_COL_NAME = "X";
    public static final String Y_COL_NAME = "Y";
    public static final String LON_COL_NAME = "Lon";
    public static final String LAT_COL_NAME = "Lat";

    private static final String FILE_EXTENSION_FLAT_OLD = ".pnf";
    private static final String FILE_EXTENSION_XML_OLD = ".pnx";

    private static final String FILE_EXTENSION_FLAT_TEXT = ".txt";
    private static final String FILE_EXTENSION_PLACEMARK = ".placemark";

    private static final int INDEX_FOR_NAME = 0;
    private static final int INDEX_FOR_LON = 1;
    private static final int INDEX_FOR_LAT = 2;
    private static final int INDEX_FOR_DESC = 3;
    private static final int INDEX_FOR_LABEL = 4;

    private final PlacemarkDescriptor placemarkDescriptor;

    private VisatApp visatApp;
    private final PropertyMap propertyMap;
    private final HashMap<Product, Band[]> productToSelectedBands;
    private final HashMap<Product, TiePointGrid[]> productToSelectedGrids;

    private Product product;
    private SortableTable placemarkTable;
    private PlacemarkListener placemarkListener;
    private BeamFileFilter pinPlacemarkFileFilter;
    private BeamFileFilter pinTextFileFilter;
    private Band[] selectedBands;
    private TiePointGrid[] selectedGrids;
    private boolean synchronizingPlacemarkSelectedState;
    private AbstractPlacemarkTableModel currentTableModel;
    private String prefixTitle;
    private PlacemarkManagerButtons buttonPane;

    public PlacemarkManagerToolView(PlacemarkDescriptor placemarkDescriptor, TableModelFactory modelFactory) {
        this.placemarkDescriptor = placemarkDescriptor;
        visatApp = VisatApp.getApp();
        propertyMap = visatApp.getPreferences();
        productToSelectedBands = new HashMap<Product, Band[]>(50);
        productToSelectedGrids = new HashMap<Product, TiePointGrid[]>(50);
        currentTableModel = modelFactory.createTableModel(placemarkDescriptor, product, null, null);
    }

    @Override
    public JComponent createControl() {
        prefixTitle = getDescriptor().getTitle();
        placemarkTable = new SortableTable();
        placemarkTable.setName("placemarkTable");
        placemarkTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        placemarkTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        placemarkTable.setRowSelectionAllowed(true);
        // IMPORTANT: We set ReorderingAllowed=false, because we export the
        // table model AS IS to a flat text file.
        placemarkTable.getTableHeader().setReorderingAllowed(false);

        ToolTipSetter toolTipSetter = new ToolTipSetter();
        placemarkTable.addMouseMotionListener(toolTipSetter);
        placemarkTable.addMouseListener(toolTipSetter);
        placemarkTable.addMouseListener(new PopupListener());
        placemarkTable.setModel(currentTableModel);
        placemarkTable.setDefaultRenderer(Float.class, new FloatTableCellRenderer(new DecimalFormat("0.000")));
        placemarkTable.getSelectionModel().addListSelectionListener(new PlacemarkTableSelectionListener());
        updateTableModel();

        final TableColumnModel columnModel = placemarkTable.getColumnModel();
        columnModel.addColumnModelListener(new ColumnModelListener());

        JScrollPane tableScrollPane = new JScrollPane(placemarkTable);
        JPanel mainPane = new JPanel(new BorderLayout(4, 4));
        mainPane.add(tableScrollPane, BorderLayout.CENTER);

        buttonPane = new PlacemarkManagerButtons(this);

        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        content.add(BorderLayout.CENTER, mainPane);
        content.add(BorderLayout.EAST, buttonPane);
        Component southExtension = getSouthExtension();
        if (southExtension != null) {
            content.add(BorderLayout.SOUTH, southExtension);
        }
        content.setPreferredSize(new Dimension(420, 200));

        if (getDescriptor().getHelpId() != null) {
            HelpSys.enableHelpKey(content, getDescriptor().getHelpId());
        }

        ProductSceneView view = visatApp.getSelectedProductSceneView();
        if (view != null) {
            setProduct(view.getProduct());
        }

        visatApp.addInternalFrameListener(new PlacemarkManagerIFL(visatApp));

        visatApp.getProductTree().addProductTreeListener(new ProductSelectionListener());


        setProduct(visatApp.getSelectedProduct());

        updateUIState();

        return content;
    }

    void applyFilteredGrids() {
        if (product != null) {
            Band[] allBands = product.getBands();
            TiePointGrid[] allGrids = product.getTiePointGrids();
            BandChooser bandChooser = new BandChooser(getWindowAncestor(),
                                                      "Available Bands And Tie Point Grids",
                                                      getDescriptor().getHelpId(), false,
                                                      allBands, selectedBands, allGrids, selectedGrids);
            if (bandChooser.show() == ModalDialog.ID_OK) {
                selectedBands = bandChooser.getSelectedBands();
                selectedGrids = bandChooser.getSelectedTiePointGrids();
                productToSelectedBands.put(product, selectedBands);
                productToSelectedGrids.put(product, selectedGrids);
                updateTableModel();
            }
        }
    }

    @Override
    public void componentOpened() {
        final CommandManager commandManager = visatApp.getCommandManager();
        final String layerCommandId = placemarkDescriptor.getShowLayerCommandId();

        ExecCommand command = (ExecCommand) commandManager.getCommand(layerCommandId);
        command.setSelected(true);
        command.execute();
    }

    protected final Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        if (this.product == product) {
            return;
        }
        Product oldProduct = this.product;
        if (oldProduct != null) {
            oldProduct.removeProductNodeListener(placemarkListener);
        }
        this.product = product;
        selectedBands = productToSelectedBands.get(this.product);
        selectedGrids = productToSelectedGrids.get(this.product);
        if (this.product != null) {
            setTitle(getDescriptor().getTitle() + " - " + this.product.getProductRefString());
            if (placemarkListener == null) {
                placemarkListener = new PlacemarkListener();
            }
            this.product.addProductNodeListener(placemarkListener);
        } else {
            setTitle(getDescriptor().getTitle());
        }

        updateTableModel();
        updatePlacemarkTable();
        updateUIState();
    }

    protected Component getSouthExtension() {
        return null;
    }


    private void updateTableModel() {
        currentTableModel.setProduct(product);
        currentTableModel.setSelectedBands(selectedBands);
        currentTableModel.setSelectedGrids(selectedGrids);
        addCellRenderer(placemarkTable.getColumnModel());
        addCellEditor(placemarkTable.getColumnModel());

    }


    protected void addCellRenderer(TableColumnModel columnModel) {
        columnModel.getColumn(0).setCellRenderer(new FloatTableCellRenderer(new DecimalFormat("0.000")));
        columnModel.getColumn(1).setCellRenderer(new FloatTableCellRenderer(new DecimalFormat("0.000")));
        columnModel.getColumn(2).setCellRenderer(new FloatTableCellRenderer(new DecimalFormat("0.000000")));
        columnModel.getColumn(3).setCellRenderer(new FloatTableCellRenderer(new DecimalFormat("0.000000")));
    }

    protected void addCellEditor(TableColumnModel columnModel) {
        columnModel.getColumn(0).setCellEditor(new PinXCellEditor(placemarkDescriptor));
        columnModel.getColumn(1).setCellEditor(new PinYCellEditor(placemarkDescriptor));
        columnModel.getColumn(2).setCellEditor(new PinLonCellEditor(placemarkDescriptor));
        columnModel.getColumn(3).setCellEditor(new PinLatCellEditor(placemarkDescriptor));
    }

    private ProductSceneView getSceneView() {
        final ProductSceneView selectedProductSceneView = visatApp.getSelectedProductSceneView();
        if (selectedProductSceneView == null && product != null) {
            final Band[] bands = product.getBands();
            for (Band band : bands) {
                final JInternalFrame internalFrame = visatApp.findInternalFrame(band);
                if (internalFrame != null) {
                    final Container contentPane = internalFrame.getContentPane();
                    if (contentPane instanceof ProductSceneView) {
                        return (ProductSceneView) contentPane;
                    }
                }
            }
            final TiePointGrid[] tiePointGrids = product.getTiePointGrids();
            for (TiePointGrid tiePointGrid : tiePointGrids) {
                final JInternalFrame internalFrame = visatApp.findInternalFrame(tiePointGrid);
                if (internalFrame != null) {
                    final Container contentPane = internalFrame.getContentPane();
                    if (contentPane instanceof ProductSceneView) {
                        return (ProductSceneView) contentPane;
                    }
                }
            }
        }
        return selectedProductSceneView;
    }

    private Pin getPinAt(final int selectedRow) {
        Pin placemark = null;
        if (product != null) {
            if (selectedRow > -1 && selectedRow < getPlacemarkGroup().getNodeCount()) {
                placemark = getPlacemarkGroup().get(selectedRow);
            }
        }
        return placemark;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // {{ Actions

    void newPin() {
        Guardian.assertNotNull("product", product);
        String[] uniquePinNameAndLabel = PlacemarkNameFactory.createUniqueNameAndLabel(placemarkDescriptor, product);
        Pin newPlacemark = new Pin(uniquePinNameAndLabel[0],
                             uniquePinNameAndLabel[1],
                             "",
                             new PixelPos(0, 0), null,
                             placemarkDescriptor.createDefaultSymbol());
        if (PlacemarkDialog.showEditPlacemarkDialog(getWindowAncestor(), product, newPlacemark, placemarkDescriptor)) {
            makePinNameUnique(newPlacemark);
            getPlacemarkGroup().add(newPlacemark);
            PlacemarkTool.setPlacemarkSelected(getPlacemarkGroup(), newPlacemark, true);
            updateUIState();
        }
    }

    void newGrid() {
        Guardian.assertNotNull("product", product);

        final PromptDialog dlg = new PromptDialog("GCP Grid", "Number of GCPs", "100", false);
        dlg.show();
        if(!dlg.IsOK())
            return;

        int numPins;
        try {
            numPins = Integer.parseInt(dlg.getValue());
        } catch(Exception e) {
            return;
        }

        final int width = product.getSceneRasterWidth();
        final int height = product.getSceneRasterHeight();
        final float ratio = width / (float)height;

        final float n = (float)Math.sqrt(numPins / ratio);
        final float m = ratio * n;
        final float spacingX = width / m;
        final float spacingY = height / n;

        getPlacemarkGroup().removeAll();

        for(float y=spacingY/2f; y < height; y+= spacingY) {

            for(float x=spacingX/2f; x < width; x+= spacingX) {

                final String[] uniquePinNameAndLabel = PlacemarkNameFactory.createUniqueNameAndLabel(placemarkDescriptor, product);
                final Pin newPin = new Pin(uniquePinNameAndLabel[0],
                             uniquePinNameAndLabel[1], "",
                             new PixelPos((int)x, (int)y), null,
                             placemarkDescriptor.createDefaultSymbol());
                getPlacemarkGroup().add(newPin);
            }
        }

        updateUIState();
    }

    void copyActivePlacemark() {
        Guardian.assertNotNull("product", product);
        Pin activePlacemark = getPlacemarkGroup().getSelectedNode();
        Guardian.assertNotNull("activePlacemark", activePlacemark);
        Pin newPlacemark = new Pin("copy_of_" + activePlacemark.getName(),
                             activePlacemark.getLabel(),
                             activePlacemark.getDescription(),
                             activePlacemark.getPixelPos(),
                             activePlacemark.getGeoPos(),
                             createPinSymbolCopy(activePlacemark.getSymbol()));
        if (PlacemarkDialog.showEditPlacemarkDialog(getWindowAncestor(), product, newPlacemark, placemarkDescriptor)) {
            makePinNameUnique(newPlacemark);
            getPlacemarkGroup().add(newPlacemark);
            PlacemarkTool.setPlacemarkSelected(getPlacemarkGroup(), newPlacemark, true);
            updateUIState();
        }
    }

    private static PlacemarkSymbol createPinSymbolCopy(PlacemarkSymbol symbol) {
        final PlacemarkSymbol placemarkSymbol = new PlacemarkSymbol(symbol.getName(), symbol.getShape());
        final ImageIcon icon = symbol.getIcon();
        if (icon != null) {
            placemarkSymbol.setIcon(icon);
        }
        if (symbol.getFillPaint() instanceof Color) {
            final Color color = (Color) symbol.getFillPaint();
            placemarkSymbol.setFillPaint(new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()));
        }
        placemarkSymbol.setFilled(symbol.isFilled());
        final Color outlineColor = symbol.getOutlineColor();
        if (outlineColor != null) {
            placemarkSymbol.setOutlineColor(new Color(outlineColor.getRed(), outlineColor.getGreen(), outlineColor.getBlue(), outlineColor.getAlpha()));
        }
        if (placemarkSymbol.getOutlineStroke() instanceof BasicStroke) {
            BasicStroke basicStroke = (BasicStroke) placemarkSymbol.getOutlineStroke();
            placemarkSymbol.setOutlineStroke(new BasicStroke(basicStroke.getLineWidth()));
        }
        final PixelPos refPoint = symbol.getRefPoint();
        placemarkSymbol.setRefPoint(new PixelPos(refPoint.x, refPoint.y));
        return placemarkSymbol;
    }

    private ProductNodeGroup<Pin> getPlacemarkGroup() {
        return placemarkDescriptor.getPlacemarkGroup(product);
    }

    void editActivePin() {
        Guardian.assertNotNull("product", product);
        Pin activePlacemark = getPlacemarkGroup().getSelectedNode();
        Guardian.assertNotNull("activePlacemark", activePlacemark);
        if (PlacemarkDialog.showEditPlacemarkDialog(getWindowAncestor(), product, activePlacemark, placemarkDescriptor)) {
            makePinNameUnique(activePlacemark);
            updateUIState();
        }
    }

    void removeSelectedPins() {
        final Collection<Pin> placemarks = getPlacemarkGroup().getSelectedNodes();
        int i = JOptionPane.showConfirmDialog(getWindowAncestor(),
                                              "Do you really want to remove " + placemarks.size() + " selected" + placemarkDescriptor.getRoleLabel() + "(s)?\n" +
                                                      "This action can not be undone.",
                                              getDescriptor().getTitle() + " - Remove " + placemarkDescriptor.getRoleLabel() + "s",
                                              JOptionPane.OK_CANCEL_OPTION);
        if (i == JOptionPane.OK_OPTION) {
            int selectedRow = placemarkTable.getSelectedRow();
            for (Pin placemark : placemarks) {
                getPlacemarkGroup().remove(placemark);
            }
            if (selectedRow >= getPlacemarkGroup().getNodeCount()) {
                selectedRow = getPlacemarkGroup().getNodeCount() - 1;
            }
            if (selectedRow >= 0) {
                placemarkTable.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
            }
            updateUIState();
        }
    }


    void zoomToActivePin() {
        Guardian.assertNotNull("product", product);
        Pin activePlacemark = getPlacemarkGroup().getSelectedNode();
        Guardian.assertNotNull("activePlacemark", activePlacemark);
        final ProductSceneView view = getSceneView();
        final PixelPos imagePos = activePlacemark.getPixelPos();  // in image coordinates on Level 0, can be null
        if (view != null && imagePos != null) {
            final ImageLayer layer = view.getBaseImageLayer();
            final AffineTransform imageToModelTransform = layer.getImageToModelTransform(0);
            final Point2D modelPos = imageToModelTransform.transform(imagePos, null);
            view.zoom(modelPos.getX(), modelPos.getY(), view.getZoomFactor());
            updateUIState();
        }
    }

    // }} Actions
    /////////////////////////////////////////////////////////////////////////////////////////////////

    private void makePinNameUnique(Pin newPlacemark) {
        if (makePlacemarkNameUnique0(newPlacemark)) {
            String roleLabel = firstLetterUp(placemarkDescriptor.getRoleLabel());
            showWarningDialog(roleLabel + " has been renamed to '" + newPlacemark.getName() + "',\n" +
                    "because a " + placemarkDescriptor.getRoleLabel() + " with the former name already exists.");
        }
    }

    /**
     * Turns the first letter of the given string to upper case.
     *
     * @param string the string to change
     * @return a changed string
     */
    private String firstLetterUp(String string) {
        String firstChar = string.substring(0, 1).toUpperCase();
        return firstChar + string.substring(1);
    }

    protected void updateUIState() {
        boolean productSelected = product != null;
        int numSelectedPins = 0;
        if (productSelected) {
            updatePlacemarkTable();
            numSelectedPins = getPlacemarkGroup().getSelectedNodes().size();
            getDescriptor().setTitle(prefixTitle + " - " + product.getDisplayName());
        } else {
            getDescriptor().setTitle(prefixTitle);
        }

        placemarkTable.setEnabled(productSelected);
        buttonPane.updateUIState(productSelected, numSelectedPins);
    }

    private void updatePlacemarkTable() {
        if (!synchronizingPlacemarkSelectedState) {
            try {
                synchronizingPlacemarkSelectedState = true;
                if (product != null) {
                    Pin[] placemarks = currentTableModel.getPlacemarks();
                    for (int i = 0; i < placemarks.length; i++) {
                        if (i < placemarkTable.getRowCount()) {
                            Pin placemark = placemarks[i];
                            int sortedRowAt = placemarkTable.getSortedRowAt(i);
                            if (placemark.isSelected() != placemarkTable.isRowSelected(sortedRowAt)) {
                                if (placemark.isSelected()) {
                                    placemarkTable.getSelectionModel().addSelectionInterval(sortedRowAt, sortedRowAt);
                                } else {
                                    placemarkTable.getSelectionModel().removeSelectionInterval(sortedRowAt,
                                                                                               sortedRowAt);
                                }
                            }
                        }
                    }
                }
                placemarkTable.revalidate();
                placemarkTable.repaint();
            } finally {
                synchronizingPlacemarkSelectedState = false;
            }
        }
    }

    void importPlacemarks(boolean allPlacemarks) {
        Pin[] placemarks;
        try {
            placemarks = loadPlacemarksFromFile();
        } catch (IOException e) {
            showErrorDialog(
                    "I/O error, failed to import " + placemarkDescriptor.getRoleLabel() + "s:\n" + e.getMessage());    /*I18N*/
            return;
        }
        if (placemarks.length == 0) {
            return;
        }
        int numPinsOutOfBounds = 0;
        int numPinsRenamed = 0;
        int numInvalids = 0;
        for (Pin placemark : placemarks) {
            if (makePlacemarkNameUnique0(placemark)) {
                numPinsRenamed++;
            }

            PixelPos newPixelPos = placemarkDescriptor.updatePixelPos(product.getGeoCoding(), placemark.getGeoPos(),
                                                                      placemark.getPixelPos());
            placemark.setPixelPos(newPixelPos);

            final PixelPos pixelPos;
            if (placemark.getPixelPos() != null) {
                pixelPos = placemark.getPixelPos();
            } else {
                pixelPos = new PixelPos();
                pixelPos.setInvalid();
            }

            if (!pixelPos.isValid()) {
                numInvalids++;
                continue;
            }

            if (product.containsPixel(pixelPos)) {
                getPlacemarkGroup().add(placemark);
            } else {
                if (placemarkDescriptor instanceof PinDescriptor) {
                    numPinsOutOfBounds++;
                } else {
                    placemark.setPixelPos(new PixelPos(0.0f, 0.0f));
                    getPlacemarkGroup().add(placemark);
                }
            }
            if (!allPlacemarks) {
                break; // import only the first one
            }
        }

        if (numInvalids > 0) {
            showWarningDialog("One or more " + placemarkDescriptor.getRoleLabel() + "s have not been imported,\n" +
                    "because they can not be assigned to a product without a geo-coding."); /*I18N*/
        }
        if (numPinsRenamed > 0) {
            showWarningDialog("One or more " + placemarkDescriptor.getRoleLabel() + "s have been renamed,\n" +
                    "because their former names are already existing."); /*I18N*/
        }
        if (numPinsOutOfBounds > 0) {
            if (numPinsOutOfBounds == placemarks.length) {
                showErrorDialog(
                        "No " + placemarkDescriptor.getRoleLabel() + "s have been imported, because their pixel\n" +
                                "positions are outside the product's bounds."); /*I18N*/
            } else {
                showErrorDialog(
                        numPinsOutOfBounds + " " + placemarkDescriptor.getRoleLabel() + "s have not been imported, because their pixel\n" +
                                "positions are outside the product's bounds."); /*I18N*/
            }
        }
    }

    private boolean makePlacemarkNameUnique0(Pin placemark) {
        if (getPlacemarkGroup().get(placemark.getName()) == placemark) {
            return false;
        }
        String name0 = placemark.getName();
        String name = name0;
        int id = 1;
        while (getPlacemarkGroup().contains(name)) {
            name = name0 + "_" + id;
        }
        if (!name0.equals(name)) {
            placemark.setName(name);
            return true;
        }
        return false;
    }

    private Pin[] loadPlacemarksFromFile() throws IOException {
        final BeamFileChooser fileChooser = new BeamFileChooser();
        String roleLabel = firstLetterUp(placemarkDescriptor.getRoleLabel());
        fileChooser.setDialogTitle("Import " + roleLabel + "s"); /*I18N*/
        setComponentName(fileChooser, "Import");
        fileChooser.addChoosableFileFilter(getTextFileFilter());
        fileChooser.setFileFilter(getPlacemarkFileFilter());
        fileChooser.setCurrentDirectory(getIODir());
        int result = fileChooser.showOpenDialog(getWindowAncestor());
        Pin[] placemarks = new Pin[0];
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                setIODir(file.getAbsoluteFile().getParentFile());
                placemarks = readPlacemarksFromFile(file);
            }
        }
        return placemarks;
    }

    void exportSelectedPlacemarks() {
        final BeamFileChooser fileChooser = new BeamFileChooser();
        String roleLabel = firstLetterUp(placemarkDescriptor.getRoleLabel());
        fileChooser.setDialogTitle("Export Selected " + roleLabel + "s");   /*I18N*/
        setComponentName(fileChooser, "Export_Selected");
        fileChooser.addChoosableFileFilter(getTextFileFilter());
        fileChooser.setFileFilter(getPlacemarkFileFilter());
        final File ioDir = getIODir();
        fileChooser.setCurrentDirectory(ioDir);
        fileChooser.setSelectedFile(new File(ioDir, placemarkDescriptor.getRoleName()));
        int result = fileChooser.showSaveDialog(getWindowAncestor());
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                if (!visatApp.promptForOverwrite(file)) {
                    return;
                }
                setIODir(file.getAbsoluteFile().getParentFile());
                BeamFileFilter beamFileFilter = fileChooser.getBeamFileFilter();
                if (!StringUtils.contains(beamFileFilter.getExtensions(), FileUtils.getExtension(file))) {
                    file = FileUtils.ensureExtension(file, beamFileFilter.getDefaultExtension());
                }
                Writer writer = null;
                try {
                    if (beamFileFilter.getFormatName().equals(getPlacemarkFileFilter().getFormatName())) {
                        writePlacemarksFile(file);
                    } else {
                        writer = new FileWriter(file);
                        writePlacemarkDataTableText(writer);
                        writer.close();
                    }
                } catch (IOException e) {
                    showErrorDialog(
                            "I/O Error.\n   Failed to export " + placemarkDescriptor.getRoleLabel() + "s.");    /*I18N*/
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        }
    }

    private void setComponentName(JComponent component, String name) {
        component.setName(getClass().getName() + "." + name);
    }

    void exportPlacemarkDataTable() {
        final BeamFileChooser fileChooser = new BeamFileChooser();
        String roleLabel = firstLetterUp(placemarkDescriptor.getRoleLabel());
        fileChooser.setDialogTitle("Export " + roleLabel + " Data Table");/*I18N*/
        setComponentName(fileChooser, "Export_Data_Table");
        fileChooser.setFileFilter(getTextFileFilter());
        final File ioDir = getIODir();
        fileChooser.setCurrentDirectory(ioDir);
        fileChooser.setSelectedFile(new File(ioDir, "Data"));
        int result = fileChooser.showSaveDialog(getWindowAncestor());

        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file != null) {
                if (!visatApp.promptForOverwrite(file)) {
                    return;
                }
                setIODir(file.getAbsoluteFile().getParentFile());
                file = FileUtils.ensureExtension(file, FILE_EXTENSION_FLAT_TEXT);
                try {
                    final Writer writer = new FileWriter(file);
                    writePlacemarkDataTableText(writer);
                    writer.close();
                } catch (IOException e) {
                    showErrorDialog("I/O Error.\nFailed to export " + roleLabel + " data table."); /*I18N*/
                }
            }
        }
    }

    private void writePlacemarkDataTableText(final Writer writer) {
        final PrintWriter pw = new PrintWriter(writer);

        final int columnCountMin = currentTableModel.getStandardColumnNames().length;
        final int columnCount = placemarkTable.getColumnCount();

        // Write file header
        pw.println("# BEAM " + placemarkDescriptor.getRoleLabel() + " export table");
        pw.println("#");
        pw.println("# Product:\t" + product.getName());
        pw.println("# Created on:\t" + new Date());
        pw.println();

        // Write header columns
        pw.print(NAME_COL_NAME + "\t");
        for (String name : currentTableModel.getStandardColumnNames()) {
            pw.print(name + "\t");
        }
        pw.print(DESC_COL_NAME + "\t");
        for (int i = columnCountMin; i < columnCount; i++) {
            pw.print(currentTableModel.getColumnName(i) + "\t");
        }
        pw.println();

        for (int sortedRow = 0; sortedRow < placemarkTable.getRowCount(); ++sortedRow) {
            if (placemarkTable.getSelectionModel().isSelectedIndex(sortedRow)) {
                final int modelRow = placemarkTable.getActualRowAt(sortedRow);
                final Pin placemark = currentTableModel.getPlacemarkAt(modelRow);

                pw.print(placemark.getName() + "\t");
                for (int col = 0; col < columnCountMin; col++) {

                    final Object value = currentTableModel.getValueAt(modelRow, col);
                    pw.print(value.toString() + "\t");
                }
                pw.print(placemark.getDescription() + "\t");
                for (int col = columnCountMin; col < columnCount; col++) {
                    final Object value = currentTableModel.getValueAt(modelRow, col);
                    pw.print(value.toString() + "\t");
                }
                pw.println();
            }
        }
        pw.close();
    }

    private void writePlacemarksFile(File outputFile) throws IOException {
        assert outputFile != null;

        XmlWriter writer = new XmlWriter(outputFile);
        final String[] tags = XmlWriter.createTags(0, "Placemarks");
        writer.println(tags[0]);
        for (Pin placemark : getPlacemarkGroup().getSelectedNodes()) {
            if (placemark != null) {
                placemark.writeXML(writer, 1);
            }
        }
        writer.println(tags[1]);
        writer.close();
    }

    private Pin[] readPlacemarksFromFile(File inputFile) throws IOException {
        assert inputFile != null;
        final DataInputStream dataInputStream = new DataInputStream(new FileInputStream(inputFile));
        byte[] magicBytes;
        try {
            magicBytes = new byte[5];
            dataInputStream.read(magicBytes);  // todo - BAD PRACTICE HERE!!!
        } finally {
            dataInputStream.close();
        }
        if (XmlWriter.XML_HEADER_LINE.startsWith(new String(magicBytes))) {
            return readPlacemarksFromXMLFile(inputFile);
        } else {
            return readPlacemarksFromFlatFile(inputFile);
        }
    }

    private Pin[] readPlacemarksFromFlatFile(File inputFile) throws IOException {
        assert inputFile != null;
        int[] columnIndexes = null;
        int biggestIndex = 0;
        ArrayList<Pin> placemarks = new ArrayList<Pin>();

        final RandomAccessFile file = new RandomAccessFile(inputFile, "r");
        int row = 0;
        while (true) {
            String line = file.readLine();
            if (line == null) {
                break;
            }
            line = line.trim(); // cut \n and \r from the end of the line
            if (line.equals("") || line.startsWith("#")) {
                continue;
            }
            String[] strings = StringUtils.toStringArray(line, "\t");
            if (columnIndexes == null) {
                int nameIndex = StringUtils.indexOf(strings, NAME_COL_NAME);
                int lonIndex = StringUtils.indexOf(strings, LON_COL_NAME);
                int latIndex = StringUtils.indexOf(strings, LAT_COL_NAME);
                int descIndex = StringUtils.indexOf(strings, DESC_COL_NAME);
                int labelIndex = StringUtils.indexOf(strings, LABEL_COL_NAME);
                if (nameIndex == -1 || lonIndex == -1 || latIndex == -1) {
                    throw new IOException("Invalid placemark file format:\n" +
                            "at least the columns 'Name', 'Lon' and 'Lat' must be given.");
                }
                biggestIndex = biggestIndex > nameIndex ? biggestIndex : nameIndex;
                biggestIndex = biggestIndex > lonIndex ? biggestIndex : lonIndex;
                biggestIndex = biggestIndex > latIndex ? biggestIndex : latIndex;
                columnIndexes = new int[5];
                columnIndexes[INDEX_FOR_NAME] = nameIndex;
                columnIndexes[INDEX_FOR_LON] = lonIndex;
                columnIndexes[INDEX_FOR_LAT] = latIndex;
                columnIndexes[INDEX_FOR_DESC] = descIndex;
                columnIndexes[INDEX_FOR_LABEL] = labelIndex;
            } else {
                row++;
                if (strings.length > biggestIndex) {
                    String name = strings[columnIndexes[PlacemarkManagerToolView.INDEX_FOR_NAME]];
                    float lon;
                    try {
                        lon = Float.parseFloat(strings[columnIndexes[PlacemarkManagerToolView.INDEX_FOR_LON]]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid placemark file format:\n" +
                                "data row " + row + ": value for 'Lon' is invalid");      /*I18N*/
                    }
                    float lat;
                    try {
                        lat = Float.parseFloat(strings[columnIndexes[PlacemarkManagerToolView.INDEX_FOR_LAT]]);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid placemark file format:\n" +
                                "data row " + row + ": value for 'Lat' is invalid");      /*I18N*/
                    }
                    String desc = null;
                    if (columnIndexes[PlacemarkManagerToolView.INDEX_FOR_DESC] >= 0 && strings.length > columnIndexes[PlacemarkManagerToolView.INDEX_FOR_DESC]) {
                        desc = strings[columnIndexes[PlacemarkManagerToolView.INDEX_FOR_DESC]];
                    }
                    String label = name;
                    if (columnIndexes[PlacemarkManagerToolView.INDEX_FOR_LABEL] >= 0 && strings.length > columnIndexes[PlacemarkManagerToolView.INDEX_FOR_LABEL]) {
                        label = strings[columnIndexes[PlacemarkManagerToolView.INDEX_FOR_LABEL]];
                    }
                    Pin placemark = new Pin(name, label, "", null, new GeoPos(lat, lon),
                                      placemarkDescriptor.createDefaultSymbol());
                    if (desc != null) {
                        placemark.setDescription(desc);
                    }
                    placemarks.add(placemark);
                } else {
                    throw new IOException("Invalid placemark file format:\n" +
                            "data row " + row + ": values for 'Name', 'Lon' and 'Lat' must be given.");   /*I18N*/
                }
            }
        }
        file.close();

        return placemarks.toArray(new Pin[placemarks.size()]);
    }

    private Pin[] readPlacemarksFromXMLFile(File inputFile) throws IOException {
        assert inputFile != null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document w3cDocument = builder.parse(inputFile);
            Document document = new DOMBuilder().build(w3cDocument);
            final Element rootElement = document.getRootElement();
            List children = rootElement.getChildren(DimapProductConstants.TAG_PLACEMARK);
            if (children.size() == 0) {
                // support for old pin XML format (.pnx)
                children = rootElement.getChildren(DimapProductConstants.TAG_PIN);
            }
            final ArrayList<Pin> placemarks = new ArrayList<Pin>(children.size());
            for (Object child : children) {
                final Element element = (Element) child;
                try {
                    placemarks.add(Pin.createPlacemark(element, placemarkDescriptor.createDefaultSymbol()));
                } catch (IllegalArgumentException e) {
                    // todo - ?
                }
            }
            return placemarks.toArray(new Pin[placemarks.size()]);
        } catch (FactoryConfigurationError error) {
            throw new IOException(error.toString());
        } catch (ParserConfigurationException e) {
            throw new IOException(e.toString(), e);
        } catch (SAXException e) {
            throw new IOException(e.toString(), e);
        } catch (IOException e) {
            throw new IOException(e.toString(), e);
        }
    }

    private BeamFileFilter getTextFileFilter() {
        if (pinTextFileFilter == null) {
            pinTextFileFilter = new BeamFileFilter("PLACEMARK_TEXT_FILE",
                                                   new String[]{FILE_EXTENSION_FLAT_TEXT, FILE_EXTENSION_FLAT_OLD},
                                                   "Placemark files - flat text format");
        }
        return pinTextFileFilter;
    }

    private BeamFileFilter getPlacemarkFileFilter() {
        if (pinPlacemarkFileFilter == null) {
            pinPlacemarkFileFilter = new BeamFileFilter("PLACEMARK_XML_FILE",
                                                        new String[]{FILE_EXTENSION_PLACEMARK, FILE_EXTENSION_XML_OLD},
                                                        "Placemark files - XML format");
        }
        return pinPlacemarkFileFilter;
    }

    private void setIODir(File dir) {
        if (propertyMap != null && dir != null) {
            propertyMap.setPropertyString(PROPERTY_KEY_IO_DIR, dir.getPath());
        }
    }

    private File getIODir() {
        File dir = SystemUtils.getUserHomeDir();
        if (propertyMap != null) {
            dir = new File(propertyMap.getPropertyString(PROPERTY_KEY_IO_DIR, dir.getPath()));
        }
        return dir;
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(getContentPane(),
                                      message,
                                      getDescriptor().getTitle() + " - Error",
                                      JOptionPane.ERROR_MESSAGE);
    }

    private void showWarningDialog(String message) {
        JOptionPane.showMessageDialog(getContentPane(),
                                      message,
                                      getDescriptor().getTitle() + " - Warning",
                                      JOptionPane.WARNING_MESSAGE);
    }

    private class PlacemarkListener implements ProductNodeListener {

        public void nodeChanged(ProductNodeEvent event) {
            ProductNode sourceNode = event.getSourceNode();
            if (sourceNode.getOwner() == placemarkDescriptor.getPlacemarkGroup(product) && sourceNode instanceof Pin) {
                updateUIState();
            }
        }

        public void nodeDataChanged(ProductNodeEvent event) {
            ProductNode sourceNode = event.getSourceNode();
            if (sourceNode.getOwner() == placemarkDescriptor.getPlacemarkGroup(product) && sourceNode instanceof Pin) {
                updateUIState();
            }
        }

        public void nodeAdded(ProductNodeEvent event) {
            ProductNode sourceNode = event.getSourceNode();
            if (sourceNode.getOwner() == placemarkDescriptor.getPlacemarkGroup(product) && sourceNode instanceof Pin) {
                currentTableModel.addPlacemark((Pin) sourceNode);
                updateUIState();
            }
        }

        public void nodeRemoved(ProductNodeEvent event) {
            ProductNode sourceNode = event.getSourceNode();
            if (sourceNode.getOwner() == placemarkDescriptor.getPlacemarkGroup(product) && sourceNode instanceof Pin) {
                currentTableModel.removePlacemark((Pin) sourceNode);
                updateUIState();
            }
        }

    }

    private class ToolTipSetter extends MouseInputAdapter {

        private int _rowIndex;

        public ToolTipSetter() {
            _rowIndex = -1;
        }

        @Override
        public void mouseExited(MouseEvent e) {
            _rowIndex = -1;
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int rowIndex = placemarkTable.rowAtPoint(e.getPoint());
            if (rowIndex != _rowIndex) {
                _rowIndex = rowIndex;
                if (_rowIndex >= 0 && _rowIndex < placemarkTable.getRowCount()) {
                    GeoPos geoPos = getPinAt(placemarkTable.getActualRowAt(_rowIndex)).getGeoPos();
                    if (geoPos != null) {
                        placemarkTable.setToolTipText(geoPos.getLonString() + " / " + geoPos.getLatString());
                    }
                }
            }
        }

    }

    private static class ColumnModelListener implements TableColumnModelListener {

        public void columnAdded(TableColumnModelEvent e) {
            int minWidth;
            final int index = e.getToIndex();
            switch (index) {
                case 0:
                case 1:
                    minWidth = 60;
                    break;
                default:
                    minWidth = 80;
            }
            TableColumnModel columnModel = (TableColumnModel) e.getSource();
            columnModel.getColumn(index).setPreferredWidth(minWidth);
        }

        public void columnRemoved(TableColumnModelEvent e) {
        }

        public void columnMoved(TableColumnModelEvent e) {
        }

        public void columnMarginChanged(ChangeEvent e) {
        }

        public void columnSelectionChanged(ListSelectionEvent e) {
        }
    }

    private class PopupListener extends MouseAdapter {

        @Override
        public void mousePressed(MouseEvent e) {
            action(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            action(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            action(e);
        }

        private void action(MouseEvent e) {
            if (e.isPopupTrigger()) {

                if (!getPlacemarkGroup().getSelectedNodes().isEmpty()) {
                    final JPopupMenu popupMenu = new JPopupMenu();
                    final JMenuItem menuItem;
                    menuItem = new JMenuItem("Copy selected data to clipboard");
                    menuItem.addActionListener(new ActionListener() {

                        public void actionPerformed(ActionEvent actionEvent) {
                            final StringWriter stringWriter = new StringWriter();
                            writePlacemarkDataTableText(stringWriter);
                            String text = stringWriter.toString();
                            text = text.replaceAll("\r\n", "\n");
                            text = text.replaceAll("\r", "\n");
                            SystemUtils.copyToClipboard(text);
                        }
                    });

                    popupMenu.add(menuItem);
                    final Point point = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), placemarkTable);
                    popupMenu.show(placemarkTable, point.x, point.y);
                }
            }
        }
    }

    /**
     * @param parent  the parent window
     * @param product the product thew given pin belongs to
     * @param pin     the pin to edit
     * @return true, if pin has changed
     * @deprecated in 4.1, {@link PlacemarkDialog#showEditPlacemarkDialog(Window,Product, org.esa.beam.framework.datamodel.Pin ,PlacemarkDescriptor)} instead
     */
    @Deprecated
    public static boolean showEditPinDialog(Window parent, Product product, Pin pin) {
        return PlacemarkDialog.showEditPlacemarkDialog(parent, product, pin, PinDescriptor.INSTANCE);
    }

    /**
     * Returns the geographical position which is equivalent to the given pixel position. Returns <code>null</code> if
     * the given pixel position is outside of the given product or the geocoding cannot get geographical positions.
     *
     * @param product  must be given and must contain a geocoding.
     * @param pixelPos must be given.
     * @return the geographical position which is equivalent to the given pixel position or <code>null</code> if the
     *         given pixel position is outside of the given product or the geocoding cannot get geographical positions.
     */
    static GeoPos getGeoPos(Product product, PixelPos pixelPos) {
        Guardian.assertNotNull("product", product);
        Guardian.assertNotNull("pixelPos", pixelPos);
        final GeoCoding geoCoding = product.getGeoCoding();
        Guardian.assertNotNull("geoCoding", geoCoding);
        if (product.containsPixel(pixelPos)) {
            return geoCoding.getGeoPos(pixelPos, null);
        }
        return null;
    }

    private class PlacemarkManagerIFL extends InternalFrameAdapter {

        private final VisatApp visatApp;

        public PlacemarkManagerIFL(VisatApp visatApp) {
            this.visatApp = visatApp;
        }

        @Override
        public void internalFrameOpened(InternalFrameEvent e) {
            updatePlacemarkManager();
        }

        @Override
        public void internalFrameActivated(InternalFrameEvent e) {
            updatePlacemarkManager();
        }

        @Override
        public void internalFrameClosing(InternalFrameEvent e) {
            updatePlacemarkManager();
        }

        @Override
        public void internalFrameClosed(InternalFrameEvent e) {
            updatePlacemarkManager();
        }

        private void updatePlacemarkManager() {
            final ProductSceneView view = visatApp.getSelectedProductSceneView();
            setProduct(view != null ? view.getProduct() : null);
        }
    }

    public static class FloatTableCellRenderer extends DefaultTableCellRenderer {

        private DecimalFormat format;

        public FloatTableCellRenderer(DecimalFormat format) {
            this.format = format;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                if (value instanceof Float && !Float.isNaN((Float) value)) {
                    label.setText(format.format(value));
                } else {
                    label.setText("n/a");
                }
            }
            return comp;
        }
    }

    private abstract class PinCellEditor extends DefaultCellEditor {

        private PlacemarkDescriptor placemarkDescriptor;
        private Border defaultBorder;

        public PinCellEditor(PlacemarkDescriptor placemarkDescriptor) {
            super(new JTextField());
            JTextField textField = (JTextField) getComponent();
            defaultBorder = textField.getBorder();
            this.placemarkDescriptor = placemarkDescriptor;
        }

        public PlacemarkDescriptor getPlacemarkDescriptor() {
            return placemarkDescriptor;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
                                                     int column) {
            JComponent component = (JComponent) super.getTableCellEditorComponent(table, value, isSelected, row,
                                                                                  column);
            component.setBorder(defaultBorder);
            return component;
        }

        @Override
        public boolean stopCellEditing() {
            JTextField textField = (JTextField) getComponent();
            float value;
            try {
                value = Float.parseFloat(textField.getText());
            } catch (NumberFormatException e) {
                ((JComponent) getComponent()).setBorder(new LineBorder(Color.red));
                return false;
            }


            Product product = PlacemarkManagerToolView.this.getPlacemarkGroup().getProduct();
            boolean validValue = validateValue(product, value);
            if (!validValue) {
                ((JComponent) getComponent()).setBorder(new LineBorder(Color.red));
                return false;
            }

            if (!super.stopCellEditing()) {
                ((JComponent) getComponent()).setBorder(new LineBorder(Color.red));
                return false;
            }

            return true;
        }

        protected abstract boolean validateValue(Product product, float value);

        @Override
        public Object getCellEditorValue() {
            JTextField textField = (JTextField) getComponent();
            try {
                return Float.parseFloat(textField.getText());
            } catch (NumberFormatException e) {
                return Float.NaN;
            }
        }
    }

    private class PinXCellEditor extends PinCellEditor {

        public PinXCellEditor(PlacemarkDescriptor placemarkDescriptor) {
            super(placemarkDescriptor);
        }

        @Override
        protected boolean validateValue(Product product, float value) {
            Pin currentPlacemark = getPlacemarkDescriptor().getPlacemarkGroup(product).getSelectedNode();
            PixelPos pixelPos = currentPlacemark.getPixelPos();
            return product.containsPixel(value, pixelPos.y);
        }
    }

    private class PinYCellEditor extends PinCellEditor {

        public PinYCellEditor(PlacemarkDescriptor placemarkDescriptor) {
            super(placemarkDescriptor);
        }

        @Override
        protected boolean validateValue(Product product, float value) {
            Pin currentPlacemark = getPlacemarkDescriptor().getPlacemarkGroup(product).getSelectedNode();
            PixelPos pixelPos = currentPlacemark.getPixelPos();
            return product.containsPixel(pixelPos.x, value);
        }
    }

    private class PinLatCellEditor extends PinCellEditor {

        public PinLatCellEditor(PlacemarkDescriptor placemarkDescriptor) {
            super(placemarkDescriptor);
        }

        @Override
        protected boolean validateValue(Product product, float lat) {
            if (lat < -90.0 || lat > 90.0) {
                return false;
            }
            GeoCoding geoCoding = product.getGeoCoding();
            if (geoCoding == null || !geoCoding.canGetGeoPos() || !geoCoding.canGetPixelPos()) {
                return true;
            }

            Pin currentPlacemark = getPlacemarkDescriptor().getPlacemarkGroup(product).getSelectedNode();
            GeoPos geoPos = new GeoPos(lat, currentPlacemark.getGeoPos().getLon());
            PixelPos pixelPos = new PixelPos(currentPlacemark.getPixelPos().x, currentPlacemark.getPixelPos().y);
            pixelPos = placemarkDescriptor.updatePixelPos(product.getGeoCoding(), geoPos, pixelPos);

            return product.containsPixel(pixelPos.x, pixelPos.y);
        }
    }

    private class PinLonCellEditor extends PinCellEditor {

        public PinLonCellEditor(PlacemarkDescriptor placemarkDescriptor) {
            super(placemarkDescriptor);
        }

        @Override
        protected boolean validateValue(Product product, float lon) {
            if (lon < -180.0 || lon > 180.0) {
                return false;
            }
            GeoCoding geoCoding = product.getGeoCoding();
            if (geoCoding == null || !geoCoding.canGetGeoPos() || !geoCoding.canGetPixelPos()) {
                return true;
            }
            Pin currentPlacemark = getPlacemarkDescriptor().getPlacemarkGroup(product).getSelectedNode();
            GeoPos geoPos = new GeoPos(currentPlacemark.getGeoPos().getLat(), lon);
            PixelPos pixelPos = new PixelPos(currentPlacemark.getPixelPos().x, currentPlacemark.getPixelPos().y);
            pixelPos = placemarkDescriptor.updatePixelPos(geoCoding, geoPos, pixelPos);
            return product.containsPixel(pixelPos.x, pixelPos.y);
        }
    }

    PlacemarkDescriptor getPlacemarkDescriptor() {
        return placemarkDescriptor;
    }

    private class ProductSelectionListener implements ProductTreeListener {

        public void productAdded(Product product) {
            setProduct(product);
        }

        public void productRemoved(Product product) {
            setProduct(null);
            productToSelectedBands.remove(product);
            productToSelectedGrids.remove(product);
        }

        public void productSelected(Product product, int clickCount) {
            setProduct(product);
        }

        public void metadataElementSelected(MetadataElement group, int clickCount) {
            setProduct(group.getProduct());
        }

        public void tiePointGridSelected(TiePointGrid tiePointGrid, int clickCount) {
            setProduct(tiePointGrid.getProduct());
        }

        public void bandSelected(Band band, int clickCount) {
            setProduct(band.getProduct());
        }
    }


    private class PlacemarkTableSelectionListener implements ListSelectionListener {

        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting() || e.getFirstIndex() == -1 || synchronizingPlacemarkSelectedState) {
                return;
            }

            try {
                synchronizingPlacemarkSelectedState = true;
                Pin[] placemarks = currentTableModel.getPlacemarks();
                for (int i = 0; i < placemarks.length; i++) {
                    Pin placemark = placemarks[i];
                    int sortedIndex = placemarkTable.getSortedRowAt(i);
                    boolean selected = placemarkTable.isRowSelected(sortedIndex);
                    if (placemark.isSelected() != selected) {
                        placemark.setSelected(selected);
                    }
                }
            } finally {
                synchronizingPlacemarkSelectedState = false;
            }

        }
    }
}
