/*
 * TripController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.stats.Stat;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import jfxtras.labs.scene.control.CalendarPicker;
import org.apache.commons.io.FileUtils;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.utils.Utils;

/**
 * TripController:
 * 
 * @author joe
 */
public class TripController extends BaseController {


/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String PathTemplateFileName = "PathTemplate.html";
    private static final double KilometersPerMile = 1.60934;
    private static final long MaxTimeBetweenWayPoints = 15 * 60 * 1000;

/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private Timer updateChoicesTimer;
    private TimerTask updateChoicesTask;
    private Map <String,Trip> choiceMap = new HashMap<>();
    private Map<String,List<Trip>> dateToTrips = new HashMap<>();
    private boolean useMiles = true;
    
    // Each of the following items defines a row of the property table
    private final GenericProperty startRange = new GenericProperty("Starting Range", "0.0", "Miles");
    private final GenericProperty endRange = new GenericProperty("Ending Range", "0.0", "Miles");
    private final GenericProperty startSOC = new GenericProperty("Starting SOC", "0.0", "%");
    private final GenericProperty endSOC = new GenericProperty("Ending SOC", "0.0", "%");
    private final GenericProperty startOdo = new GenericProperty("Starting Odo", "0.0", "Miles");
    private final GenericProperty endOdo = new GenericProperty("Ending Odo", "0.0", "Miles");
    private final ObservableList<GenericProperty> data = FXCollections.observableArrayList(
            startRange, endRange, startSOC, endSOC, startOdo, endOdo);
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private CalendarPicker calendarPicker;
    @FXML private Button mapItButton;
    @FXML private ComboBox<String> selectionComboBox;
    
    // The Property TableView and its Columns
    @FXML private TableView<GenericProperty> propertyTable;
    @FXML private TableColumn<GenericProperty,String> propNameColumn;
    @FXML private TableColumn<GenericProperty,String> propValColumn;
    @FXML private TableColumn<GenericProperty,String> propUnitsColumn;
    
/*------------------------------------------------------------------------------
 *
 * UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    @FXML void mapItHandler(ActionEvent event) {
        Trip t = choiceMap.get(selectionComboBox.getSelectionModel().getSelectedItem());
        if (t == null) {
            Tesla.logger.warning("Asked to MapIt, but no selected item. Looks like a bug.");
            return;
        }
        
        String map = getMapFromTemplate(t);
        try {
            File tempFile = File.createTempFile("VTTrip", ".html");
            FileUtils.write(tempFile, map);
            appContext.app.getHostServices().showDocument(tempFile.toURI().toString());
        } catch (IOException ex) {
            Tesla.logger.warning("Unable to create temp file");
            // TO DO: Pop up a dialog!
        }
    }

    @FXML void todayHandler(ActionEvent event) {
        calendarPicker.calendars().clear();
        calendarPicker.calendars().add(new GregorianCalendar());
    }
    
    @FXML void selectionHandler(ActionEvent event) {
        Trip t = choiceMap.get(selectionComboBox.getSelectionModel().getSelectedItem());
        reflectTripInfo(t);
        mapItButton.setDisable(false);
    }

    @FXML void clearSelection(ActionEvent event) {
        mapItButton.setDisable(true);
        calendarPicker.calendars().clear();
    }

    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() {
        mapItButton.setDisable(true);
        calendarPicker.calendars().addListener(new ListChangeListener<Calendar>() {
            @Override public void onChanged(ListChangeListener.Change<? extends Calendar> change) {
                if (updateChoicesTask != null) updateChoicesTask.cancel();
                updateChoicesTask = new UpdateChoicesTask();
                updateChoicesTimer.schedule(updateChoicesTask, 100);
            }
        });
        
        // Prepare the property table
        propNameColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("name") );
        propValColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("value") );
        propUnitsColumn.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("units") );
        propertyTable.setItems(data);
    }
    
    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            List<Trip> trips = readTrips();
            for (Trip t : trips) {
                WayPoint wp = t.waypoints.get(0);
                Date d = new Date(wp.timestamp);
                String dateKey = keyFromDate(d);
                List<Trip> tripsForDay = dateToTrips.get(dateKey);
                if (tripsForDay == null) {
                    tripsForDay = new ArrayList<>();
                    dateToTrips.put(dateKey, tripsForDay);
                }
                tripsForDay.add(t);
            }
        }
        
        GUIState.State guiState = appContext.lastKnownGUIState.get();
        useMiles = guiState.distanceUnits.equalsIgnoreCase("mi/hr");
        if (appContext.simulatedUnits.get() != null)
            useMiles = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
        
        startRange.setUnits(useMiles ? "Miles" : "Km");
        endRange.setUnits(useMiles ? "Miles" : "Km");
        startOdo.setUnits(useMiles ? "Miles" : "Km");
        endOdo.setUnits(useMiles ? "Miles" : "Km");
    }

    @Override protected void refresh() { }

    @Override protected void reflectNewState() { }

    @Override protected void appInitialize() {
        updateChoicesTimer = new Timer("00 - VTLocStore_UCT", true);
    }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectTripInfo(Trip t) {
        if (t == null || t.waypoints.isEmpty()) return;
        WayPoint start = t.waypoints.get(0);
        WayPoint end   = t.waypoints.get(t.waypoints.size()-1);
        
        double cvt = useMiles ? 1.0 : KilometersPerMile;
        updateStartEndProps(
                StatsStore.EstRangeKey, start.timestamp, end.timestamp, 
                startRange, endRange, cvt);
        updateStartEndProps(
                StatsStore.SOCKey, start.timestamp, end.timestamp,
                startSOC, endSOC, 1.0);
        updateStartEndProps(startOdo, endOdo, start.odo, end.odo, cvt);
    }
    
    private void updateStartEndProps(
            String statType, long startTime, long endTime,
            GenericProperty start, GenericProperty end,
            double conversionFactor) {
        List<Stat.Sample> stats = appContext.valuesForRange(
                statType, startTime, endTime);
        if (stats == null) {
            start.setValue("--");
            end.setValue("--");
        } else {
            start.setValue(String.valueOf(stats.get(0).value*conversionFactor));
            end.setValue(String.valueOf(stats.get(stats.size()-1).value*conversionFactor));
        }
    }

    private void updateStartEndProps(
            GenericProperty start, GenericProperty end,
            double startVal, double endVal, double conversionFactor) {
        start.setValue(String.valueOf(startVal*conversionFactor));
        end.setValue(String.valueOf(endVal*conversionFactor));
    }
    
    private class UpdateChoicesTask extends TimerTask {
        @Override public void run() {
            Platform.runLater(new Runnable() {
                @Override public void run() { updateTripSelections(); }
            });
        }
    };
    
    /**
     * Must be run on the FX Application Thread
     */
    private void updateTripSelections() {
        choiceMap.clear();
        mapItButton.setDisable(true);
        selectionComboBox.getItems().clear();
        if (calendarPicker.calendars().size() == 0) {
            return;
        }
        for (Calendar c : calendarPicker.calendars()) {
            String dateKey = keyFromDate(c.getTime());
            List<Trip> trips = dateToTrips.get(dateKey);
            if (trips != null) {
                for (final Trip t : trips) {
                    final String id = dateKey + " @ " + hourAndMinutes(t.waypoints.get(0).timestamp);
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            selectionComboBox.getItems().add(id);
                            choiceMap.put(id, t);
                        }
                    });
                }
            }
        }
    }
        
    private String keyFromDate(Date d) {
        return String.format("%1$tY-%1$tm-%1$td", d);
    }
    
    private String hourAndMinutes(long time) {
        return String.format("%1$tH:%1$tM", new Date(time));
    }
    
    private String getMapFromTemplate(Trip t) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(PathTemplateFileName));
        return template.fillIn(
                "WAYPOINTS", t.asJSON(),
                "TITLE", "Tesla Path on " + new Date(t.waypoints.get(0).timestamp));
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods and Classes for handling Trips and WayPoints
 * 
 *----------------------------------------------------------------------------*/
    
    public List<Trip> readTrips() {
        List<Trip> trips = new ArrayList<>();
        
        List<WayPoint> wps = new ArrayList<>();
        Map<Long,Map<String,Double>> rows = appContext.locationStore.getData();
        for (Map.Entry<Long,Map<String,Double>> row : rows.entrySet()) {
            Map<String,Double> vals = row.getValue();
            wps.add(new WayPoint(
                row.getKey(), // timestamp
                safeGet(vals, LocationStore.LatitudeKey),
                safeGet(vals, LocationStore.LongitudeKey),
           (int)safeGet(vals, LocationStore.HeadingKey),
                safeGet(vals, LocationStore.SpeedKey),
                safeGet(vals, LocationStore.OdometerKey)));
        }

        Trip currentTrip = new Trip();
        WayPoint lastWayPoint = new WayPoint();
        for (WayPoint wp : wps) {
            if ((wp.timestamp - lastWayPoint.timestamp > MaxTimeBetweenWayPoints)) {
                addNonEmptyTrip(trips, currentTrip);
                currentTrip = new Trip();
            }
            if (!tooCloseToIncludeInTrip(wp, lastWayPoint)) {
                currentTrip.addWayPoint(wp);
                lastWayPoint = wp;
            }
        }
        addNonEmptyTrip(trips, currentTrip);
        
        return trips;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods to check proximity between points
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean tooCloseToIncludeInTrip(WayPoint wp1, WayPoint wp2) {
        return tooClose(wp1, wp2, 10, (2 * 1000), 5);
    }
    
    private boolean tooClose(WayPoint wp1, WayPoint wp2, double maxTurn, long minTime, int minDist) {
        double turn =  180.0 - Math.abs((Math.abs(wp1.heading - wp2.heading)%360.0) - 180.0);
        if (turn > maxTurn) return false;
        
        if (Math.abs(wp1.timestamp - wp2.timestamp) < minTime)
            return true;
        
        double meters = GeoUtils.distance(wp1.lat, wp1.lng, wp2.lat, wp2.lng);
        return (meters < minDist);
    }
    
    private void addNonEmptyTrip(List<Trip> trips, Trip trip) {
        if (!trip.waypoints.isEmpty()) {
            for (WayPoint wp: trip.waypoints) {
                if (wp.speed != 0) {
                    trips.add(trip);
                    return;
                }
            }
        }
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods
 * 
 *----------------------------------------------------------------------------*/

    private double safeGet(Map<String,Double> vals, String key) {
        Double val = vals.get(key);
        return (val == null) ? 0.0 : val.doubleValue();
    }
    
/*------------------------------------------------------------------------------
 *
 * Public - Nested Classes
 * 
 *----------------------------------------------------------------------------*/
    
    public class Trip {
        List<WayPoint> waypoints;
        
        public Trip() {
            waypoints = new ArrayList<>();
        }
        
        public void addWayPoint(WayPoint wp) { 
            waypoints.add(wp);
        }
        
        public String asJSON() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            sb.append("[\n");
            for (WayPoint wp : waypoints) {
                if (first) first = false;
                else sb.append(",\n");
                sb.append(wp.toString());
            }
            sb.append("];\n");
            return sb.toString();
            
        }
        
        @Override public String toString() { return asJSON(); }
    }
    
    public class WayPoint {
        long timestamp;
        double lat, lng;
        int heading;
        double speed;
        double odo;
        
        public WayPoint() { this(0,0,0,0,0,0); }
        
        public WayPoint(long timestamp, double lat, double lng, int heading, double speed, double odo) {
            this.timestamp = timestamp;
            this.lat = lat;
            this.lng = lng;
            this.heading = heading;
            this.speed = speed;
            this.odo = odo;
        }
        
        public WayPoint(SnapshotState.State state) {
            this.timestamp = state.timestamp;
            this.lat = state.estLat;
            this.lng = state.estLng;
            this.heading = state.heading;
            this.speed = state.speed;
            this.odo = state.odometer;
        }
        
        public String asJSON() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("    timestamp: \"");
            sb.append(String.format("%1$tm/%1$td/%1$ty %1$tH:%1$tM:%1$tS", new Date(timestamp)));
            sb.append("\",\n");
            sb.append("    lat: ").append(lat).append(",\n");
            sb.append("    lng: ").append(lng).append(",\n");
            sb.append("    speed: ").append(speed).append(",\n");
            sb.append("    heading: ").append(heading).append("\n");
            //sb.append("    odometer: ").append(odo).append("\n");
            sb.append("}\n");
            return sb.toString();
        }
        
        @Override public String toString() { return asJSON(); }
    }
}
