package com.bc.ceres.swing.binding.internal;

import javax.swing.JTextField;
import java.awt.event.ActionListener;

/**
 * A binding for a {@link javax.swing.JTextField} component.
 *
 * @author Norman Fomferra
 * @version $Revision: 1.1 $ $Date: 2010-02-10 19:57:11 $
 * @since Ceres 0.6
 * @deprecated since Ceres 0.10, use {@link TextComponentAdapter} directly
 */
@Deprecated
public class TextFieldAdapter extends TextComponentAdapter implements ActionListener {

    public TextFieldAdapter(JTextField textField) {
        super(textField);
    }
}
