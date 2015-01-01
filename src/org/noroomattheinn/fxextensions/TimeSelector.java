/*
 * TimeSelector.java - Copyright(c) 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Oct 19, 2014
 */
package org.noroomattheinn.fxextensions;


import java.util.Calendar;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ComboBox;
import org.noroomattheinn.utils.CalTime;

/**
 * A UI Helper class for dealing with time selections. Times are represented by
 * three combo boxes. The first lets you select the hour (1-12), the second lets
 * you picked the minute (0-55 in 5 minute increments), and the last lets you
 * select AM or PM.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class TimeSelector {
    final private ComboBox<String>  hour;
    final private ComboBox<String>  min;
    final private ComboBox<String>  amPM;
    private ObjectProperty<CalTime> prop;
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/
    
    /**
     * Create a new instance given the underlying combo boxes.
     * @param hour  A combo box representing the hour
     * @param min   A combo box representing the minute
     * @param amPM  A combo box representing AM or PM
     */
    public TimeSelector(ComboBox<String> hour, ComboBox<String> min, ComboBox<String> amPM) {
        this.hour = hour; this.min = min; this.amPM = amPM;
    }

    /**
     * Bind this TimeSelector bidirectionally to a property containing a CalTime.
     * If the property changes then the UI will be updated to reflect that.
     * If the UI changes, the property will be updated.
     * 
     * @param prop 
     */
    public void bind(ObjectProperty<CalTime> prop) {
        this.prop = prop;

        this.hour.valueProperty().addListener(updateValue);
        this.min.valueProperty().addListener(updateValue);
        this.amPM.valueProperty().addListener(updateValue);

        set(prop.get());
        prop.addListener(new ChangeListener<CalTime>() {
            @Override public void changed(ObservableValue ov, CalTime t, CalTime t1) {
                set(t1); } });
    }
    
    /**
     * Enable or disable the controls
     * @param enable    If true, enable. If false, disable
     */
    public void enable(boolean enable) {
        hour.setDisable(!enable);
        min.setDisable(!enable);
        amPM.setDisable(!enable);
    }
    
    /**
     * Return the time as a composite integer
     * @return An integer of the form: hhmm where hh is in the range 0-23
     */
    public int getHoursAndMinutes() {
        int h = Integer.valueOf(hour.getValue());
        int m = Integer.valueOf(min.getValue());
        if (amPM.getValue().equals("PM")) {
            if (h != 12)
                h = (h + 12) % 24;
        } else {    // It's AM
            if (h == 12)
                h = 0;
        }
        return h * 100 + m;
    }

    /**
     * Update the selection given
     * @param hhmm  An integer representing a composite time of the form hhmm
     *              where hh is in the range 0-23
     */
    public void setHoursAndMinutes(int hhmm) {
        int hours = hhmm / 100;
        int minutes = hhmm % 100;
        minutes = (minutes/5) * 5;
        boolean isPM = (hours >= 12);
        if (hours == 0) {
            hours = 12;
        }
        if (isPM && !(hours == 12))
            hours -= 12;
        hour.getSelectionModel().select(String.format("%02d", hours));
        min.getSelectionModel().select(String.format("%02d", minutes));
        amPM.getSelectionModel().select(isPM ? "PM" : "AM");
    }
    
/*------------------------------------------------------------------------------
 *
 * Private Utility Methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void set(CalTime c) {
        hour.setValue(twoDigitString(c.get(Calendar.HOUR)));
        min.setValue(twoDigitString((c.get(Calendar.MINUTE)/5)*5));
        amPM.setValue(c.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");
    }

    private String twoDigitString(int val) { return String.format("%02d", val); };
    
    private ChangeListener<String> updateValue = new ChangeListener<String>() {
        @Override public void changed(
                ObservableValue<? extends String> ov, String t, String t1) {
            if (prop == null) return;
            prop.set(new CalTime(hour.getValue(), min.getValue(), amPM.getValue()));
        }
    };

}