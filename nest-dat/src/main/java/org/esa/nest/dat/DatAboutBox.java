
package org.esa.nest.dat;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.esa.beam.framework.ui.ModalDialog;

import com.bc.ceres.core.Assert;

/**
 * This class pertains to the "about" dialog box for the VISAT application.
 */
public class DatAboutBox extends ModalDialog {

    public DatAboutBox() {
        this(new JButton[]{
                new JButton(),
                new JButton(),
        });
    }

    private DatAboutBox(JButton[] others) {
        super(DatApp.getApp().getMainFrame(), "About DAT", ModalDialog.ID_OK, others, null);    /*I18N*/

        JButton creditsButton = others[0];
        creditsButton.setText("Credits...");  /*I18N*/
        creditsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showCreditsDialog();
            }
        });

        JButton systemButton = others[1];
        systemButton.setText("System Info...");  /*I18N*/
        systemButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showSystemDialog();
            }
        });

        URL resource = getClass().getResource("/about_nest.jpg");
        Assert.notNull(resource);
        Icon icon = new ImageIcon(resource);

        JLabel imageLabel = new JLabel(icon);
        JPanel dialogContent = new JPanel(new BorderLayout());
        String versionText = getVersionHtml();
        JLabel versionLabel = new JLabel(versionText);

        JPanel labelPane = new JPanel(new BorderLayout());
        labelPane.add(BorderLayout.NORTH, versionLabel);

        dialogContent.setLayout(new BorderLayout(4, 4));
        dialogContent.add(BorderLayout.WEST, imageLabel);
        dialogContent.add(BorderLayout.EAST, labelPane);

        setContent(dialogContent);
    }

    @Override
    protected void onOther() {
        // override default behaviour by doing nothing
    }

    private void showCreditsDialog() {
        final ModalDialog modalDialog = new ModalDialog(getJDialog(), "Credits", ID_OK, null); /*I18N*/
        final String credits = getCreditsHtml();
        final JLabel creditsPane = new JLabel(credits); /*I18N*/
        modalDialog.setContent(creditsPane);
        modalDialog.show();
    }


    private void showSystemDialog() {
        final ModalDialog modalDialog = new ModalDialog(getJDialog(), "System Info", ID_OK, null);
        final Object[][] sysInfo = getSystemInfo();
        final JTable sysTable = new JTable(sysInfo, new String[]{"Property", "Value"}); /*I18N*/
        final JScrollPane systemScroll = new JScrollPane(sysTable);
        systemScroll.setPreferredSize(new Dimension(400, 400));
        modalDialog.setContent(systemScroll);
        modalDialog.show();
    }

    private static String getVersionHtml() {
        final String pattern =
                "<html>" +
                "<b>NEST DAT Version {0}</b>" +
                "<br>(c) Copyright 2007 by Array Systems Computing Inc. and contributors. All rights reserved." +
                "<br>Visit http://www.array.ca/nest" +
                "<br>" +
                "<b>BEAM </b>" +
                "<br>(c) Copyright 2007 by Brockmann Consult and contributors. All rights reserved." +
                "<br>Visit http://www.brockmann-consult.de/beam/" +
                "<br>" +
                "<br>This program has been developed under contract to ESA (ESRIN)." +
                "<br>Visit http://envisat.esa.int/services/" +
                "<br>" +
                "<br>This program is free software; you can redistribute it and/or modify it" +
                "<br>under the terms of the GNU General Public License as published by the" +
                "<br>Free Software Foundation. This program is distributed in the hope it will be" +
                "<br>useful, but WITHOUT ANY WARRANTY; without even the implied warranty" +
                "<br>of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE." +
                "<br>See the GNU General Public License for more details." +
                "<br>" +
                "<br>This product includes software developed by Unidata and NCSA" +
                "<br>Visit http://www.unidata.ucar.edu/ and http://hdf.ncsa.uiuc.edu/" +
                "</html>";
        return MessageFormat.format(pattern, DatApp.APP_VERSION); /*I18N*/
    }

    private static String getCreditsHtml() {
        return
                "<html>" +
                "<br>The NEST team at Array Systems Computing is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Rajesh Jha</b> (project manager)<br>" +
                "&nbsp;&nbsp;<b>Shengli Dai</b> (project scientist)<br>" +
                "&nbsp;&nbsp;<b>Luis Veci</b> (software lead)<br>" +
                "&nbsp;&nbsp;<b>Daniel Danu</b> (developer)<br>" +
                "&nbsp;&nbsp;<b>Jun Lu</b> (developer)<br>" +
                "&nbsp;&nbsp;<b>Steven Truelove</b> (developer)<br>" +
                "</td><td>" +
                "&nbsp;&nbsp;<b>Andrew Taylor</b> (IT support)<br>" +
                "&nbsp;&nbsp;<b>Iris Buchan</b> (quality assurance)<br>" +
                "&nbsp;&nbsp;<b>Nisso Keslassy</b> (contracts officer)<br>" +
                "&nbsp;&nbsp;<b>Kay Simpson</b> (technical writing)<br>" +
                "&nbsp;&nbsp;<b>Sue Miller</b> (configuration management)<br>" +
                "&nbsp;&nbsp;<b></b> <br>" +
                "</td></tr>" +
                "</table>" +
                "<br><hr>The BEAM team at Brockmann Consult is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Tom Block</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Carsten Brockmann</b> (quality control)<br>" +
                "&nbsp;&nbsp;<b>Sabine Embacher</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Olga Faber</b> (testing)<br>" +
                "&nbsp;&nbsp;<b>Norman Fomferra</b> (project lead)<br>" +
                "&nbsp;&nbsp;<b>Uwe Krämer</b> (Mac OS X porting)<br>" +
                "</td><td>" +
                "&nbsp;&nbsp;<b>Des Murphy</b> (contract management)<br>" +
                "&nbsp;&nbsp;<b>Michael Paperin</b> (web development)<br>" +
                "&nbsp;&nbsp;<b>Marco Peters</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Ralf Quast</b> (programming)<br>" +
                "&nbsp;&nbsp;<b>Kerstin Stelzer</b> (quality control)<br>" +
                "&nbsp;&nbsp;<b>Marco Zühlke</b> (programming)<br>" +
                "</td></tr>" +
                "</table>" +
                "<br><hr>The NEST team at ESA/ESRIN is:" +
                "<table border=0>" +
                "<tr><td>" +
                "&nbsp;&nbsp;<b>Marcus Engdahl</b> (technical officer)<br>" +
                "&nbsp;&nbsp;<b>Andrea Minchella</b> (scientist)<br>" +
                "&nbsp;&nbsp;<b>Nuno Miranda</b> (scientist)<br>" +
                "</td></tr>" +
                "</table>" +
                "<br><hr>The NEST developers would also like to say thank you to" +
                "<br>&nbsp;&nbsp;<b>Sun</b> for the beautiful programming language they have invented," +
                "<br>&nbsp;&nbsp;<b>IntelliJ</b> for the best IDE in the world," +
                "<br>&nbsp;&nbsp;<b>Eclipse.org</b> for the second best IDE in the world," +
                "<br>&nbsp;&nbsp;<b>JIDE Software</b> for a great docking framework," +
                "<br>&nbsp;&nbsp;all companies and organisations supporting the open-source idea." +
                "<br><br><hr>" +
                "</html>"; /*I18N*/
    }

    private static Object[][] getSystemInfo() {

        List<Object[]> data = new ArrayList<Object[]>();

        Properties sysProps = null;
        try {
            sysProps = System.getProperties();
        } catch (RuntimeException e) {
        }
        if (sysProps != null) {
            String[] names = new String[sysProps.size()];
            Enumeration<?> e = sysProps.propertyNames();
            for (int i = 0; i < names.length; i++) {
                names[i] = (String) e.nextElement();
            }
            Arrays.sort(names);
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                String value = sysProps.getProperty(name);
                data.add(new Object[]{name, value});
            }
        }

        Object[][] dataArray = new Object[data.size()][2];
        for (int i = 0; i < dataArray.length; i++) {
            dataArray[i] = data.get(i);
        }
        return dataArray;
    }
}
