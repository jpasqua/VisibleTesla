package org.noroomattheinn.visibletesla.dialogs;

import org.noroomattheinn.fxextensions.VTDialog;
import com.google.common.collect.Range;
import java.util.Calendar;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import jfxtras.labs.scene.control.CalendarPicker;
import org.noroomattheinn.visibletesla.Prefs;


public class DateRangeDialog extends VTDialog.Controller {
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    private Calendar start = null;
    private Calendar end = null;
    private boolean selectedAll = false;
    private Calendar highlightStart, highlightEnd;
    
/*------------------------------------------------------------------------------
 *
 * Internal State - UI Components
 * 
 *----------------------------------------------------------------------------*/
    @FXML private Button allButton;
    @FXML private Button cancelButton;
    @FXML private Button selectedButton;
    @FXML private CalendarPicker calendarPicker;
    @FXML private ComboBox<String> quickSelect;
    
/*------------------------------------------------------------------------------
 *
 * UI Initialization and Action Handlers
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML void initialize() {
        quickSelect.valueProperty().addListener(handleQuickSelect);
        calendarPicker.setCalendarRangeCallback(new Callback<CalendarPicker.CalendarRange,java.lang.Void>() {
            @Override public Void call(CalendarPicker.CalendarRange p) {
                highlightDays(p.getStartCalendar());
                return null;
            } });
    }

    @FXML void buttonHandler(ActionEvent event) {
        Button b = (Button)event.getSource();
        if (b == allButton) {
            selectedAll = true;
        } else if (b == selectedButton) {
            List<Calendar> selection = calendarPicker.calendars();
            if (selection == null || selection.isEmpty()) {
                start = end = null;
            } else {
                start = selection.get(0);
                end = selection.get(selection.size()-1);
            }
        } else if (b == cancelButton) {
            start = end = null;
        }
        dialogStage.close();
    }
    
/*==============================================================================
 * -------                                                               -------
 * -------              Public Interface To This Class                   ------- 
 * -------                                                               -------
 *============================================================================*/

    public static DateRangeDialog show(Stage stage, Calendar sd, Calendar ed) {
        DateRangeDialog drd = VTDialog.<DateRangeDialog>load(
            DateRangeDialog.class.getResource("DateRangeDialog.fxml"),
                "Date Range for Export", stage);
        drd.setInitialValues(sd, ed);
        drd.show();
        return drd;
    }
    
    public static Range<Long> getExportPeriod(Stage stage) {
        return getExportPeriod(stage, null, null);
    }
    
    public static Range<Long> getExportPeriod(Stage stage, Calendar sd, Calendar ed) {
        DateRangeDialog drd = DateRangeDialog.show(stage, sd, ed);

        if (drd.selectedAll()) {
            return Range.closed(0L, Long.MAX_VALUE);
        }
        Calendar start = drd.getStartCalendar();
        Calendar end = drd.getEndCalendar();
        if (start == null) {
            return null;
        } else {
            long t0 = start.getTimeInMillis();
            long t1 = end.getTimeInMillis();
            if (t1 < t0) return Range.closed(t1, t0);
            return Range.closed(t0, t1);
        }
    }

    public boolean selectedAll() { return selectedAll; }
    
    public Calendar getStartCalendar() {
        if (start == null) return null;
        return decodeDay(encodeDay(start), false);
    }
    
    public Calendar getEndCalendar() {
        if (end == null) return null;
        return decodeDay(encodeDay(end), true);
    }
    
/*------------------------------------------------------------------------------
 *
 * UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    private final ChangeListener<String> handleQuickSelect = new ChangeListener<String>() {
        @Override public void changed(
                ObservableValue<? extends String> ov, String t, String t1) {
            Calendar now = Calendar.getInstance();
            Prefs.LoadPeriod period = Prefs.nameToLoadPeriod.get(t1);
            start = null;
            end = null;

            switch (period) {
                case Last7:
                    end = now;
                    start = Calendar.getInstance();
                    start.add(Calendar.DAY_OF_YEAR, -6);
                    break;
                case Last14:
                    end = now;
                    start = Calendar.getInstance();
                    start.add(Calendar.DAY_OF_YEAR, -13);
                    break;
                case Last30:
                    end = now;
                    start = Calendar.getInstance();
                    start.add(Calendar.DAY_OF_YEAR, -29);
                    break;                        
                case ThisWeek:
                    end = now;
                    start = Calendar.getInstance();
                    start.set(Calendar.DAY_OF_WEEK,
                            now.getActualMinimum(Calendar.DAY_OF_WEEK));
                    break;                        
                case ThisMonth:
                    end = now;
                    start = Calendar.getInstance();
                    start.set(Calendar.DAY_OF_MONTH,
                            now.getActualMinimum(Calendar.DAY_OF_MONTH));
                    break;
                case None:
                default: break;
            }
            
            ObservableList<Calendar> calendars = calendarPicker.calendars();
            calendars.clear();
            if (start != null) {
                int startDay = encodeDay(start);
                int endDay = encodeDay(end);
                int curDay = startDay;
                while (curDay <= endDay) {
                    Calendar c = decodeDay(curDay, curDay == endDay);
                    calendars.add(c);
                    Calendar next = (Calendar)c.clone();
                    next.add(Calendar.DAY_OF_YEAR, 1);
                    curDay = encodeDay(next);
                }
            }
        }
    };
    
/*------------------------------------------------------------------------------
 *
 * Private Utility methods
 * 
 *----------------------------------------------------------------------------*/
    
    private void setInitialValues(Calendar sd, Calendar ed) {
        if (sd != null) this.highlightStart = sd;
        if (ed != null) this.highlightEnd = ed;
    }
            
    private int encodeDay(Calendar day) {
        int encoded = day.get(Calendar.YEAR);
        encoded = (encoded * 1000) + day.get(Calendar.DAY_OF_YEAR);
        return encoded;
    }
    
    private Calendar decodeDay(int encoded, boolean endOfDay) {
        int year = encoded / 1000;
        int dayOfYear = encoded % 1000;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.DAY_OF_YEAR, dayOfYear);
        c.set(Calendar.HOUR_OF_DAY, endOfDay ? 23 : 0);
        c.set(Calendar.MINUTE, endOfDay ? 59 : 0);
        c.set(Calendar.SECOND, endOfDay ? 59 : 0);
        return c;
    }
    
    private Calendar beginningOfDay(Calendar day) {
        Calendar b = dupeCal(day);
        b.set(Calendar.HOUR_OF_DAY, 0);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 1);
        return b;
    }
    
    private Calendar endOfDay(Calendar day) {
        Calendar e = dupeCal(day);
        e.set(Calendar.HOUR_OF_DAY, 23);
        e.set(Calendar.MINUTE, 59);
        e.set(Calendar.SECOND, 59);
        return e;
    }
    
    private Calendar lastDayOfMonth(Calendar day) {
        Calendar e = dupeCal(day);
        e.set(Calendar.DAY_OF_MONTH, 1);
        e.add(Calendar.MONTH, 1);
        e.add(Calendar.DAY_OF_MONTH, -1);
        return endOfDay(e);
    }
    
    private Calendar dupeCal(Calendar orig) {
        Calendar dupe = Calendar.getInstance();
        dupe.setTimeInMillis(orig.getTimeInMillis());
        return dupe;
    }
    
    private void highlightDays(Calendar month) {
        if (highlightStart == null || highlightEnd == null) return;
        calendarPicker.highlightedCalendars().clear();

        Calendar cur = beginningOfDay(highlightStart);
        Calendar last = endOfDay(highlightEnd);
        Calendar lastOfMonth = lastDayOfMonth(month);
        while (cur.before(last) && cur.before(lastOfMonth)) {
            calendarPicker.highlightedCalendars().add(dupeCal(cur));
            cur.add(Calendar.DAY_OF_YEAR, 1);
        }
    }
    
}
