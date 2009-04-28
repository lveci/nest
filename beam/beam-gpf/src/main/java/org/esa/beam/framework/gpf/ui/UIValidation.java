package org.esa.beam.framework.gpf.ui;

/**
 * Created by IntelliJ IDEA.
 * User: lveci
 * Date: Feb 13, 2008
 * To change this template use File | Settings | File Templates.
 */
public class UIValidation {

    private boolean state = false;
    private String msg = "";

    public UIValidation(boolean theState, String theMessage) {
        state = theState;
        msg = theMessage;
    }

    public boolean getState() {
        return state;
    }

    public String getMsg() {
        return msg;
    }

}
