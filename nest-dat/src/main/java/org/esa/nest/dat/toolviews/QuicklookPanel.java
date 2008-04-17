package org.esa.nest.dat.toolviews;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.Assert;
import com.bc.ceres.core.SubProgressMonitor;
import com.bc.ceres.swing.progress.DialogProgressMonitor;
import org.esa.beam.framework.ui.ImageDisplay;
import org.esa.beam.framework.ui.UIUtils;
import org.esa.beam.framework.ui.application.ToolView;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.util.Debug;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.visat.toolviews.stat.PagePanel;

import javax.media.jai.PlanarImage;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * A pane within the statistcs window which displays a quicklook.
 *
 * @author Luis Veci
 */
class QuicklookPanel extends PagePanel {

    private final static String TITLE_PREFIX = "Quicklook";    /*I18N*/

    private QuickLookPane quicklooktPane;
    private JButton computeButton;

    public QuicklookPanel(final ToolView parentDialog) {
        super(parentDialog);
    }

    @Override
    protected String getTitlePrefix() {
        return TITLE_PREFIX;
    }

    @Override
    protected void initContent() {
        createUI();
        updateContent();
    }

    @Override
    protected void updateContent() {

        computeButton.setEnabled(getProduct() != null && getRaster() != null);
    }

    @Override
    protected boolean mustUpdateContent() {
        return isRasterChanged();
    }

    private void createUI() {

        final Icon icon = UIUtils.loadImageIcon("icons/Gears20.gif");

        computeButton = new JButton("Compute");     /*I18N*/
        computeButton.setMnemonic('A');
        computeButton.setEnabled(false);
        computeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                computeQuicklook();
            }
        });
        computeButton.setIcon(icon);
        final JPanel controlPanel = new JPanel(new BorderLayout(2, 2));
        controlPanel.add(computeButton, BorderLayout.NORTH);

        setLayout(new BorderLayout(2, 2));
        this.add(controlPanel, BorderLayout.EAST);
    }

    private void computeQuicklook()
    {
        if(quicklooktPane != null)
            this.remove(quicklooktPane);

        quicklooktPane = new QuickLookPane();
        this.add(quicklooktPane, BorderLayout.WEST);
        repaint();
    }

    protected String getDataAsText() {
        return "";
    }


    public class QuickLookPane extends JPanel {

        private ImageDisplay imageCanvas;
        private JScrollPane imageScrollPane;
        private int thumbNailSubSampling;
        private SwingWorker thumbnailLoader;

        ProgressMonitor pm;

        private final int MAX_THUMBNAIL_WIDTH = 400;

        public QuickLookPane() {
            createUI();
        }

        private void createUI() {

            createImagePane();
            //setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
        }

        private void createImagePane()
        {
            setThumbnailSubsampling();
            final ProductSubsetDef psd = createThumbnailSubsetDef();

            Dimension imgSize = psd.getSceneRasterSize(getProduct().getSceneRasterWidth(),
                                                       getProduct().getSceneRasterHeight());
            imageCanvas = new ImageDisplay(imgSize.width, imgSize.height);
            imageCanvas.setSize(imgSize.width, imgSize.height);

            pm = new DialogProgressMonitor(this, "Loading data...",
                                           Dialog.ModalityType.TOOLKIT_MODAL);

            thumbnailLoader = new SwingWorker() {
                private IOException exception;

                @Override
                protected Object doInBackground() throws Exception {
                    exception = null;
                    PlanarImage thumbnail = null;
                    try {
                        thumbnail = createThumbNailImage(psd, pm);
                    } catch (IOException e) {
                        exception = e;
                    }
                    return thumbnail;
                }

                @Override
                protected void done() {
                    super.done();

                    PlanarImage thumbnail = null;
                    try {
                        thumbnail = (PlanarImage) get();
                    } catch (Exception e) {
                        // occures if sbuset dialog is canceled
                        // dont show a message to user but we can log to the console
                        Debug.trace("Thumbnail creation interrupted");
                    }

                    if (thumbnail != null) {
                        imageCanvas.setImage(thumbnail);
                        repaint();
                    }
                }

            };
            thumbnailLoader.execute();

            imageScrollPane = new JScrollPane(imageCanvas);
            imageScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            imageScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
            imageScrollPane.getViewport().setExtentSize(new Dimension(MAX_THUMBNAIL_WIDTH, 2* MAX_THUMBNAIL_WIDTH));

            setLayout(new BorderLayout(2, 2));
            add(imageScrollPane, BorderLayout.WEST);
        }

        private PlanarImage createThumbNailImage(ProductSubsetDef psd, ProgressMonitor pm) throws IOException {
            Assert.notNull(pm, "pm");
            Product productSubset = getProduct().createSubset(psd, null, null);

            Band thumbNailBand = productSubset.getBand(getRaster().getName());

            pm.beginTask("Creating thumbnail image", 3);
            final PlanarImage planarImage;
            try {
                thumbNailBand.readRasterDataFully(SubProgressMonitor.create(pm, 1));

                BufferedImage image =  thumbNailBand.createRgbImage(SubProgressMonitor.create(pm, 1));

                productSubset.dispose();
                planarImage = PlanarImage.wrapRenderedImage(image);
                pm.worked(1);
            } finally {
                pm.done();
            }
            return planarImage;
        }

        private void setThumbnailSubsampling() {
            int w = getProduct().getSceneRasterWidth();

            thumbNailSubSampling = w / MAX_THUMBNAIL_WIDTH;
            if (thumbNailSubSampling <= 1) {
                thumbNailSubSampling = 1;
            }
        }

        private ProductSubsetDef createThumbnailSubsetDef() {
            ProductSubsetDef psd = new ProductSubsetDef("undefined");
            psd.setIgnoreMetadata(false);
            psd.setNodeNames(new String[]{ProductUtils.findSuitableQuicklookBandName(getProduct())});
            psd.setSubSampling(thumbNailSubSampling, thumbNailSubSampling);
            return psd;
        }
    }

}