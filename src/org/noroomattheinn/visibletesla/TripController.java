/*
 * TripController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialogs;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import jfxtras.labs.scene.control.CalendarPicker;
import org.apache.commons.io.FileUtils;
import org.noroomattheinn.tesla.ChargeState;
import org.noroomattheinn.tesla.StreamState;
import org.noroomattheinn.timeseries.Row;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.SimpleTemplate;
import org.noroomattheinn.utils.Utils;
import org.noroomattheinn.visibletesla.data.Trip;
import org.noroomattheinn.visibletesla.data.VTData;
import org.noroomattheinn.visibletesla.data.WayPoint;

import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * TripController: Manage the recording, selection and display of Trips
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class TripController extends BaseController {

/*------------------------------------------------------------------------------
 *
 * Constants and Enums
 * 
 *----------------------------------------------------------------------------*/
    
    private static final String IncludeGraphKey = "TR_INCLUDE_GRAPH";
    private static final String SnapToRoadKey = "TR_SNAP";
    private static final String PathTemplateFileName = "PathTemplate.html";
    private static final long MaxTimeBetweenWayPoints = 15 * 60 * 1000;
    private static final String RangeRowName = "Range";
    private static final String OdoRowName = "Odometer";
    private static final String ConsumptionRowName = "Cons.";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean useMiles;
    
    private Map<String,List<Trip>> dateToTrips = new HashMap<>();
    private Map<String,Trip> selectedTrips = new HashMap<>();
    
    // Each of the following items defines a row of the property table
    private final GenericProperty rangeRow = new GenericProperty(RangeRowName, "0.0", "0.0");
    private final GenericProperty socRow = new GenericProperty("SOC (%)", "0.0", "0.0");
    private final GenericProperty odoRow = new GenericProperty(OdoRowName, "0.0", "0.0");
    private final GenericProperty powerRow = new GenericProperty("Energy (kWh)", "0.0", "0.0");
    private final GenericProperty comsumptionRow = new GenericProperty(ConsumptionRowName, "0.0", "0.0");
    private final ObservableList<GenericProperty> data = FXCollections.observableArrayList(
            rangeRow, socRow, odoRow, powerRow, comsumptionRow);
    
/*------------------------------------------------------------------------------
 *
 * UI Elements
 * 
 *----------------------------------------------------------------------------*/
    
    @FXML private CalendarPicker calendarPicker;
    @FXML private Button mapItButton;
    @FXML private Button exportItButton;
    @FXML private ListView<String> availableTripsView;
    @FXML private CheckBox includeGraph;
    @FXML private CheckBox snapToRoad;
    
    // The Property TableView and its Columns
    @FXML private TableView<GenericProperty> propertyTable;
    @FXML private TableColumn<GenericProperty,String> propNameCol;
    @FXML private TableColumn<GenericProperty,String> propStartCol;
    @FXML private TableColumn<GenericProperty,String> propEndCol;
    
/*------------------------------------------------------------------------------
 *
 * UI Action Handlers
 * 
 *----------------------------------------------------------------------------*/

    private List<Trip> getSelectedTrips() {
        ArrayList<Trip> selection = new ArrayList<>();
        for (String item : availableTripsView.getSelectionModel().getSelectedItems()) {
            Trip t = selectedTrips.get(item);
            if (t != null) selection.add(t);
        }
        Collections.sort(selection, new Comparator<Trip>() {
            @Override
            public int compare(Trip o1, Trip o2) {
                return Long.signum(o1.firstWayPoint().getTime() - o2.firstWayPoint().getTime());
            }
        });
        return selection;
    }
    
    @FXML void exportItHandler(ActionEvent event) {
        String initialDir = prefs.storage().get(
                App.LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Trip as KMZ");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(app.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                prefs.storage().put(App.LastExportDirKey, enclosingDirectory);
            if (vtData.exportTripsAsKML(getSelectedTrips(), file)) {
                Dialogs.showInformationDialog(
                        app.stage, "Your data has been exported",
                        "Data Export Process" , "Export Complete");
            } else {
                Dialogs.showWarningDialog(
                        app.stage, "There was a problem exporting your trip data to KMZ",
                        "Data Export Process" , "Export Failed");
            }
        }
    }
    
    @FXML void mapItHandler(ActionEvent event) {
        List<Trip> trips = getSelectedTrips();
        
        String map = getMapFromTemplate(trips);
        try {
            File tempFile = File.createTempFile("VTTrip", ".html");
            FileUtils.write(tempFile, map);
            app.showDocument(tempFile.toURI().toString());
        } catch (IOException ex) {
            logger.warning("Unable to create temp file");
            // TO DO: Pop up a dialog!
        }
    }

    @FXML void todayHandler(ActionEvent event) {
        calendarPicker.calendars().clear();
        calendarPicker.calendars().add(new GregorianCalendar());
    }
    
    @FXML void endTripHandler(ActionEvent event) {
        endCurrentTrip();
    }
    

    @FXML void clearSelection(ActionEvent event) {
        mapItButton.setDisable(true);
        exportItButton.setDisable(true);
        calendarPicker.calendars().clear();
        availableTripsView.getItems().clear();
        selectedTrips.clear();
    }

    
/*------------------------------------------------------------------------------
 *
 * Methods overridden from BaseController
 * 
 *----------------------------------------------------------------------------*/
    
    @Override protected void fxInitialize() {
        mapItButton.setDisable(true);
        exportItButton.setDisable(true);
        
        availableTripsView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        availableTripsView.getSelectionModel().getSelectedItems().addListener(new ListChangeListener<String>() {
            @Override public void onChanged(ListChangeListener.Change<? extends String> change) {
                reflectSelection();
            }
        });
        
        calendarPicker.calendars().addListener(new ListChangeListener<Calendar>() {
            @Override public void onChanged(ListChangeListener.Change<? extends Calendar> change) {
                updateTripSelections();
            }
        });
        
        calendarPicker.setCalendarRangeCallback(new Callback<CalendarPicker.CalendarRange,java.lang.Void>() {
            @Override public Void call(CalendarPicker.CalendarRange p) {
                highlightDaysWithTrips(p.getStartCalendar());
                return null;
            } });
        
        includeGraph.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                prefs.storage().putBoolean(IncludeGraphKey, t1);
            }
        });
        
        snapToRoad.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                prefs.storage().putBoolean(SnapToRoadKey, t1);
            }
        });
        
        // Prepare the property table
        propNameCol.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("name") );
        propStartCol.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("value") );
        propEndCol.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("units") );
        propertyTable.setItems(data);

    }

    @Override protected void activateTab() {
<<<<<<< HEAD
        String units = vtVehicle.unitType() == Utils.UnitType.Imperial ? " (mi)" : " (km)";
        useMiles = vtVehicle.unitType() == Utils.UnitType.Imperial;

=======
        useMiles = vtVehicle.unitType() == Utils.UnitType.Imperial;
        String units = useMiles ? " (mi)" : " (km)";
>>>>>>> refs/remotes/jpasqua/master
        rangeRow.setName(RangeRowName + units);
        odoRow.setName(OdoRowName + units);
        comsumptionRow.setName(ConsumptionRowName+" (Wh/"+units.substring(2,4)+")");
    }
    
    @Override protected void initializeState() {
        includeGraph.setSelected(
                prefs.storage().getBoolean(IncludeGraphKey, false));
        snapToRoad.setSelected(
                prefs.storage().getBoolean(SnapToRoadKey, false));
        readTrips();
    }

    @Override protected void refresh() { }

/*------------------------------------------------------------------------------
 *
 * PRIVATE - Utility Methods and Classes
 * 
 *----------------------------------------------------------------------------*/
    
    private void reflectTripInfo() {
        List<Trip> trips = getSelectedTrips();
        if (trips == null || trips.isEmpty()) return;
        WayPoint start = trips.get(0).firstWayPoint();
        WayPoint end   = trips.get(trips.size()-1).lastWayPoint();
        
        double cvt = useMiles ? 1.0 : Utils.KilometersPerMile;
        updateStartEndProps(
                VTData.EstRangeKey, start.getTime(), end.getTime(), 
                rangeRow, cvt);
        updateStartEndProps(
                VTData.SOCKey, start.getTime(), end.getTime(),
                socRow, 1.0);
                
        updateStartEndProps(odoRow, start.getOdo(), end.getOdo(), cvt);
        
        double power = 0.0;
        for (Trip t:trips) {
            power += t.estimateEnergy();
        }
        updateStartEndProps(powerRow, 0.0, power, 1.0);
        double tripLength=end.getOdo()-start.getOdo();
        updateStartEndProps(comsumptionRow, 0.0, (power*1000.0)/(tripLength*cvt),1.0);
    }
    
    private void updateStartEndProps(
            String statType, long startTime, long endTime,
            GenericProperty prop, double conversionFactor) {
        NavigableMap<Long,Row> rows = vtData.getRangeOfLoadedRows(startTime, endTime);

        double startValue = rows.firstEntry().getValue().get(VTData.schema, statType);
        double endValue = rows.lastEntry().getValue().get(VTData.schema, statType);
        prop.setValue(String.format("%.1f", startValue * conversionFactor));
        prop.setUnits(String.format("%.1f", endValue * conversionFactor));
    }

    private void updateStartEndProps(
            GenericProperty prop,
            double startVal, double endVal, double conversionFactor) {
        prop.setValue(String.format("%.1f", startVal*conversionFactor));
        prop.setUnits(String.format("%.1f", endVal*conversionFactor));
    }
    
    private void reflectSelection() {
        reflectTripInfo();
        mapItButton.setDisable(selectedTrips.isEmpty());
        exportItButton.setDisable(selectedTrips.isEmpty());
    }
    
    /**
     * Must be run on the FX Application Thread
     */
    private void updateTripSelections() {
        mapItButton.setDisable(true);
        exportItButton.setDisable(true);
        selectedTrips.clear();
        availableTripsView.getItems().clear();
        if (calendarPicker.calendars().size() == 0) {
            return;
        }
        double cvt = useMiles ? 1.0 : Utils.KilometersPerMile;
        for (Calendar c : calendarPicker.calendars()) {
            String dateKey = keyFromDate(c.getTime());
            List<Trip> trips = dateToTrips.get(dateKey);
            if (trips != null) {
                for (Trip t : trips) {
                    String id = String.format("%s @ %s, %.1f %s",
                        dateKey, hourAndMinutes(t.firstWayPoint().getTime()),
                        t.distance()*cvt, useMiles ? "mi" : "km");
                    selectedTrips.put(id, t);
                    availableTripsView.getItems().add(id);
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
    
    private String getMapFromTemplate(List<Trip> trips) {
        SimpleTemplate template = new SimpleTemplate(getClass().getResourceAsStream(PathTemplateFileName));
        
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        boolean first = true;
        for (Trip t : trips) {
            if (includeGraph.isSelected()) { t.addElevationData(); }
            if (!first) sb.append(",\n");
            sb.append(t.asJSON(useMiles));
            first = false;
        }
        sb.append("]\n");
        Date date = new Date(trips.get(0).firstWayPoint().getTime());
        return template.fillIn(
                "TRIPS", sb.toString(),
                "TITLE", "Tesla Path on " + date,
                "EL_UNITS", useMiles ? "feet" : "meters",
                "SP_UNITS", useMiles ? "mph" : "km/h",
                "INCLUDE_GRAPH", includeGraph.isSelected() ? "true" : "false",
                "SNAP", snapToRoad.isSelected() ? "true" : "false",
                "GMAP_API_KEY", prefs.useCustomGoogleAPIKey.get() ?
                    prefs.googleAPIKey.get() :
                    Prefs.GoogleMapsAPIKey);
    }
    
    private boolean sameMonth(Calendar month, Calendar day) {
        return (month.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
                month.get(Calendar.MONTH) == day.get(Calendar.MONTH));
    }

    private void highlightDaysWithTrips(Calendar month) {
        List<Calendar> daysToHighlight = new ArrayList<>();
        for (List<Trip> trips : dateToTrips.values()) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(trips.get(0).firstWayPoint().getTime());
            if (sameMonth(month, day)) daysToHighlight.add(day);
        }
        calendarPicker.highlightedCalendars().clear();
        calendarPicker.highlightedCalendars().addAll(daysToHighlight);
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods and Classes for handling Trips and WayPoints
 * 
 *----------------------------------------------------------------------------*/
    
    private void readTrips() {
        Map<Long,Row> rows = vtData.getAllLoadedRows();
        for (Row r : rows.values()) {
            double lat = r.get(VTData.schema, VTData.LatitudeKey);
            double lng = r.get(VTData.schema, VTData.LongitudeKey);
            double odo = r.get(VTData.schema, VTData.OdometerKey);
            if (lat == 0.0 && lng == 0.0 || odo == 0.0) continue;
            WayPoint wp = new WayPoint(
                r.timestamp,
                odo,
                r.get(VTData.schema, VTData.SpeedKey),
                r.get(VTData.schema, VTData.HeadingKey),
                lat, lng, Double.NaN,
                r.get(VTData.schema, VTData.PowerKey),
                r.get(VTData.schema, VTData.SOCKey));
            handleNewWayPoint(wp);
        }
        endCurrentTrip();
        
        // Start listening for new WayPoints
        vtData.lastStoredStreamState.addTracker(new Runnable() {
            @Override public void run() {
                StreamState ss = vtData.lastStoredStreamState.get();
                ChargeState cs = vtData.lastStoredChargeState.get();
                handleNewWayPoint(
                    new WayPoint(
                        ss.timestamp, ss.odometer, ss.speed,
                        ss.heading, ss.estLat, ss.estLng, Double.NaN,
                        ss.power, cs.batteryPercent));
            }
        });
        
        highlightDaysWithTrips(Calendar.getInstance());
    }
    
    private void endCurrentTrip() {
        if (tripInProgress == null) return;
        
        if (tripInProgress.distance() > 0.1) {
            updateTripData(tripInProgress);
        }
        
        tripInProgress = null;
    }
    
    private Trip tripInProgress = null;
    
    private void handleNewWayPoint(WayPoint wp) {
        if (tripInProgress == null) {
            // No Trip in progress, start one
            startNewTrip(wp);
            return;
        }
        
        WayPoint last = tripInProgress.lastWayPoint();
        if ((wp.getTime() - last.getTime() > MaxTimeBetweenWayPoints)) {
            // Finish the old trip and start a new one
            endCurrentTrip();
            startNewTrip(wp);
            return;
        }
        
        if (thereWasMotion(wp, last)) {
            // Add to the current Trip
            tripInProgress.addWayPoint(wp);
        }
    }
    
    private void startNewTrip(WayPoint wp) {
        tripInProgress = new Trip();
        tripInProgress.addWayPoint(wp);
    }
    
    private void updateTripData(Trip t) {
        WayPoint wp = t.firstWayPoint();
        Date d = new Date(wp.getTime());
        String dateKey = keyFromDate(d);
        List<Trip> tripsForDay = dateToTrips.get(dateKey);
        if (tripsForDay == null) {
            tripsForDay = new ArrayList<>();
            dateToTrips.put(dateKey, tripsForDay);
        }
        tripsForDay.add(t);
        
        if (dateIsSelected(d))  {
            Platform.runLater(new Runnable() {
                @Override public void run() { updateTripSelections(); } });
        }
        
    }

    private boolean dateIsSelected(Date d) {
        Calendar tripDay = Calendar.getInstance();
        tripDay.setTime(d);
        int yr = tripDay.get(Calendar.YEAR);
        int doy = tripDay.get(Calendar.DAY_OF_YEAR);
        
        for (Calendar c : calendarPicker.calendars()) {
            if (c.get(Calendar.YEAR) == yr && c.get(Calendar.DAY_OF_YEAR) == doy) {
                return true;
            }
        }
        return false;
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods to check proximity between points
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean thereWasMotion(WayPoint wp1, WayPoint wp2) {
        double turn =  180.0 - Math.abs((Math.abs(wp1.getHeading() - wp2.getHeading())%360.0) - 180.0);
        double meters = GeoUtils.distance(wp1.getLat(), wp1.getLng(), wp2.getLat(), wp2.getLng());

        return (meters >= 5 || (turn > 10 && meters > 3.0));
    }

}