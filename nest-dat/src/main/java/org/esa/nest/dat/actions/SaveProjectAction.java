
package org.esa.nest.dat.actions;

import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.framework.ui.command.ExecCommand;
import org.esa.nest.dat.util.XMLSupport;

import org.w3c.dom.*;

/**
 * This action saves the project and asks the user for the new file location.
 *
 * @author lveci
 * @version $Revision: 1.1 $ $Date: 2008-01-23 19:51:56 $
 */
public class SaveProjectAction extends ExecCommand {

    @Override
    public void actionPerformed(final CommandEvent event) {

        Document doc = XMLSupport.CreateXML();
        Element root = doc.createElement("Project");
        doc.appendChild(root);

        Element em = doc.createElement("newElem");
        em.appendChild(doc.createTextNode("value"));
        root.appendChild(em);

        XMLSupport.SaveXML(doc);
    }


}
