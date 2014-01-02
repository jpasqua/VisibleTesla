/*
 * TripController.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla;

import org.noroomattheinn.visibletesla.stats.Stat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
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
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import jfxtras.labs.scene.control.CalendarPicker;
import org.apache.commons.io.FileUtils;
import org.noroomattheinn.tesla.GUIState;
import org.noroomattheinn.tesla.SnapshotState;
import org.noroomattheinn.tesla.Tesla;
import org.noroomattheinn.tesla.Vehicle;
import org.noroomattheinn.utils.GeoUtils;
import org.noroomattheinn.utils.GeoUtils.ElevationData;
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
    
    private static final String IncludeGraphKey = "TR_INCLUDE_GRAPH";
    private static final String PathTemplateFileName = "PathTemplate.html";
    private static final String CarIconResource = "org/noroomattheinn/TeslaResources/02_loc_arrow@2x.png";
    private static final double KilometersPerMile = 1.60934;
    private static final long MaxTimeBetweenWayPoints = 15 * 60 * 1000;
    private static final String RangeRowName = "Range";
    private static final String OdoRowName = "Odometer";
    
/*------------------------------------------------------------------------------
 *
 * Internal State
 * 
 *----------------------------------------------------------------------------*/
    
    private boolean useMiles = true;
    
    private Map<String,List<Trip>> dateToTrips = new HashMap<>();
    private Map<String,Trip> selectedTrips = new HashMap<>();
    
    // Each of the following items defines a row of the property table
    private final GenericProperty rangeRow = new GenericProperty(RangeRowName, "0.0", "0.0");
    private final GenericProperty socRow = new GenericProperty("SOC (%)", "0.0", "0.0");
    private final GenericProperty odoRow = new GenericProperty(OdoRowName, "0.0", "0.0");
    private final ObservableList<GenericProperty> data = FXCollections.observableArrayList(
            rangeRow, socRow, odoRow);
    
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
                return Long.signum(o1.firstWayPoint().timestamp - o2.firstWayPoint().timestamp);
            }
        });
        return selection;
    }
    
    @FXML void exportItHandler(ActionEvent event) {
        String initialDir = appContext.persistentState.get(
                DataStore.LastExportDirKey, System.getProperty("user.home"));
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Trip as KMZ");
        fileChooser.setInitialDirectory(new File(initialDir));

        File file = fileChooser.showSaveDialog(appContext.stage);
        if (file != null) {
            String enclosingDirectory = file.getParent();
            if (enclosingDirectory != null)
                appContext.persistentState.put(DataStore.LastExportDirKey, enclosingDirectory);
            KMLExporter ke = new KMLExporter();
            if (ke.export(getSelectedTrips(), file)) {
                Dialogs.showInformationDialog(
                        appContext.stage, "Your data has been exported",
                        "Data Export Process" , "Export Complete");
            } else {
                Dialogs.showWarningDialog(
                        appContext.stage, "There was a problem exporting your trip data to KMZ",
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
        
        includeGraph.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean t1) {
                appContext.persistentState.putBoolean(IncludeGraphKey, t1);
            }
        });
        
        // Prepare the property table
        propNameCol.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("name") );
        propStartCol.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("value") );
        propEndCol.setCellValueFactory( new PropertyValueFactory<GenericProperty,String>("units") );
        propertyTable.setItems(data);

    }

    
    @Override protected void prepForVehicle(Vehicle v) {
        if (differentVehicle()) {
            includeGraph.setSelected(
                    appContext.persistentState.getBoolean(IncludeGraphKey, false));
            readTrips();
        }
        
        GUIState.State guiState = appContext.lastKnownGUIState.get();
        useMiles = guiState.distanceUnits.equalsIgnoreCase("mi/hr");
        if (appContext.simulatedUnits.get() != null)
            useMiles = (appContext.simulatedUnits.get() == Utils.UnitType.Imperial);
        
        rangeRow.setName(RangeRowName + (useMiles ? " (mi)" : " (km)"));
        odoRow.setName(OdoRowName + (useMiles ? " (mi)" : " (km)"));
    }

    @Override protected void refresh() { }

    @Override protected void reflectNewState() { }

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
        
        double cvt = useMiles ? 1.0 : KilometersPerMile;
        updateStartEndProps(
                StatsStore.EstRangeKey, start.timestamp, end.timestamp, 
                rangeRow, cvt);
        updateStartEndProps(
                StatsStore.SOCKey, start.timestamp, end.timestamp,
                socRow, 1.0);
        updateStartEndProps(odoRow, start.odo, end.odo, cvt);
    }
    
    private void updateStartEndProps(
            String statType, long startTime, long endTime,
            GenericProperty prop, double conversionFactor) {
        List<Stat.Sample> stats = appContext.valuesForRange(
                statType, startTime, endTime);
        if (stats == null) {
            prop.setValue("--");
            prop.setUnits("--");
        } else {
            prop.setValue(String.format("%.1f", stats.get(0).value*conversionFactor));
            prop.setUnits(String.format("%.1f", stats.get(stats.size()-1).value*conversionFactor));
        }
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
        double cvt = useMiles ? 1.0 : KilometersPerMile;
        for (Calendar c : calendarPicker.calendars()) {
            String dateKey = keyFromDate(c.getTime());
            List<Trip> trips = dateToTrips.get(dateKey);
            if (trips != null) {
                for (Trip t : trips) {
                    String id = String.format("%s @ %s, %.1f %s",
                        dateKey, hourAndMinutes(t.firstWayPoint().timestamp),
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
            if (includeGraph.isSelected() && t.firstWayPoint().decoration == null) {
                decorateWayPoints(t);                
            }
            if (!first) sb.append(",\n");
            sb.append(t.asJSON());
            first = false;
        }
        sb.append("]\n");
        Date date = new Date(trips.get(0).firstWayPoint().timestamp);
        return template.fillIn(
                "TRIPS", sb.toString(),
                "TITLE", "Tesla Path on " + date,
                "EL_UNITS", "meters",
                "INCLUDE_GRAPH", includeGraph.isSelected() ? "true" : "false",
                "GMAP_API_KEY", appContext.prefs.googleAPIKey.get());
    }
    
/*------------------------------------------------------------------------------
 *
 * PRIVATE - Methods and Classes for handling Trips and WayPoints
 * 
 *----------------------------------------------------------------------------*/
    
    private void readTrips() {
        Map<Long,Map<String,Double>> rows = appContext.locationStore.getData();
        for (Map.Entry<Long,Map<String,Double>> row : rows.entrySet()) {
            Map<String,Double> vals = row.getValue();
            WayPoint wp = new WayPoint(
                row.getKey(), // timestamp
                safeGet(vals, LocationStore.LatitudeKey),
                safeGet(vals, LocationStore.LongitudeKey),
           (int)safeGet(vals, LocationStore.HeadingKey),
                safeGet(vals, LocationStore.SpeedKey),
                safeGet(vals, LocationStore.OdometerKey));
            handleNewWayPoint(wp);
        }
        
        if (tripInProgress != null) {   // Finish off this last trip
            handleNewWayPoint(new WayPoint(Long.MAX_VALUE, 0, 0, 0, 0, 0));
            tripInProgress = null;
        }
        
        // Start listening for new WayPoints
        appContext.lastKnownSnapshotState.addListener(new ChangeListener<SnapshotState.State>() {
            WayPoint last = new WayPoint(Long.MAX_VALUE, 0, 0, 0, 0, 0);
            
            @Override public void changed(
                    ObservableValue<? extends SnapshotState.State> ov,
                    SnapshotState.State old, SnapshotState.State cur) {
                handleNewWayPoint(new WayPoint(cur));
            }
        });
    }
    
    private Trip tripInProgress = null;
    
    private void handleNewWayPoint(WayPoint wp) {
        if (tripInProgress == null) {
            tripInProgress = new Trip();
            tripInProgress.addWayPoint(wp);
            return;
        }
        WayPoint last = tripInProgress.lastWayPoint();
        if ((wp.timestamp - last.timestamp > MaxTimeBetweenWayPoints)) {
            if (tripInProgress.distance() > 0.1) {
                updateTripData(tripInProgress);
            }
            tripInProgress = null;
        }
        if (!tooCloseToIncludeInTrip(wp, last)) {
            if (tripInProgress == null) tripInProgress = new Trip();
            tripInProgress.addWayPoint(wp);
        }
    }
    
    private void updateTripData(Trip t) {
        WayPoint wp = t.firstWayPoint();
        Date d = new Date(wp.timestamp);
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
    
    private boolean tooCloseToIncludeInTrip(WayPoint wp1, WayPoint wp2) {
        return tooClose(wp1, wp2, 10, (2 * 1000), 5);
    }
    
    private boolean tooClose(WayPoint wp1, WayPoint wp2, double maxTurn, long minTime, int minDist) {
        double turn =  180.0 - Math.abs((Math.abs(wp1.heading - wp2.heading)%360.0) - 180.0);
        if (turn > maxTurn) return false;
        
        if (Math.abs(wp1.timestamp - wp2.timestamp) < minTime)  return true;
        
        double meters = GeoUtils.distance(wp1.lat, wp1.lng, wp2.lat, wp2.lng);
        return (meters < minDist);
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

    private double elevation = 0.0;

    private void decorateWayPoints(Trip t) {
        long start = t.firstWayPoint().timestamp;
        long end   = t.lastWayPoint().timestamp;

        NavigableMap<Long,Double> socVals = mapFromSamples(
                appContext.valuesForRange(StatsStore.SOCKey, start, end));
        NavigableMap<Long,Double> pwrVals = mapFromSamples(
                appContext.valuesForRange(StatsStore.PowerKey, start, end));
        
        for (WayPoint wp : t.waypoints) {
            wp.decoration = new HashMap<>();
            long wpTime = wp.timestamp;

            Long socKey = socVals.floorKey(wpTime);
            if (socKey == null) socKey = socVals.firstEntry().getKey();
            wp.decoration.put(StatsStore.SOCKey, socVals.get(socKey));

            Long pwrKey = pwrVals.floorKey(wpTime);
            if (pwrKey == null) pwrKey = pwrVals.firstEntry().getKey();
            wp.decoration.put(StatsStore.PowerKey, pwrVals.get(pwrKey));
        }
        // Assumes wp.decoration has been initialized for all waypoints
        addElevations(t.waypoints);
    }

    private NavigableMap<Long,Double> mapFromSamples(List<Stat.Sample> samples) {
        NavigableMap<Long,Double> map = new TreeMap<>();
        for (Stat.Sample s : samples)
            map.put(s.timestamp, s.value);
        return map;
    }
    
    private void addElevations(List<WayPoint> waypoints) {
        List<ElevationData> edl = GeoUtils.getElevations(waypoints);
        if (edl == null) return;
        for (int i = edl.size()-1; i >= 0; i--) {
            double e = edl.get(i).elevation;
            // if (useMiles) e = metersToFeet(e); // Always use meters for now
            waypoints.get(i).decoration.put("L_ELV", osd(e));
        }
    }
    
    private double osd(double value) { return Math.round(value * 10.0) / 10.0; }
    private double metersToFeet(double meters) { return meters * 3.28084; }
    
/*------------------------------------------------------------------------------
 *
 * Public - Nested Classes
 * 
 *----------------------------------------------------------------------------*/
    
    public class Trip {
        private List<WayPoint> waypoints;
        
        public Trip() {
            waypoints = new ArrayList<>();
        }
        
        public void addWayPoint(WayPoint wp) { 
            waypoints.add(wp);
        }
        
        public double distance() {
            if (waypoints.isEmpty()) return 0.0;
            double startLoc = firstWayPoint().odo;
            double endLoc = lastWayPoint().odo;
            return (endLoc - startLoc);
        }
        
        public boolean isEmpty() { return waypoints.isEmpty(); }
        public WayPoint firstWayPoint() { return waypoints.get(0); }
        public WayPoint lastWayPoint() { return waypoints.get(waypoints.size()-1); }
        
        public String asJSON() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            sb.append("[\n");
            for (WayPoint wp : waypoints) {
                if (first) first = false;
                else sb.append(",\n");
                sb.append(wp.toString());
            }
            sb.append("]\n");
            return sb.toString();
            
        }
        
        @Override public String toString() { return asJSON(); }

    }
    
    public class WayPoint implements GeoUtils.LocationSource {
        long timestamp;
        double lat, lng;
        int heading;
        double speed;
        double odo;
        Map<String,Double> decoration;
        
        public WayPoint() { this(0,0,0,0,0,0); }
        
        public WayPoint(long timestamp, double lat, double lng, int heading, double speed, double odo) {
            this.timestamp = timestamp;
            this.lat = lat;
            this.lng = lng;
            this.heading = heading;
            this.speed = speed;
            this.odo = odo;
            decoration = null;
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
            if (decoration != null) {
                for (Map.Entry<String,Double> kv : decoration.entrySet()) {
                    sb.append("    ").append(kv.getKey()).append(": ")
                      .append(kv.getValue()).append(",\n");
                }
            }
            sb.append("    lat: ").append(lat).append(",\n");
            sb.append("    lng: ").append(lng).append(",\n");
            sb.append("    speed: ").append(speed).append(",\n");
            sb.append("    heading: ").append(heading).append("\n");
            //sb.append("    odometer: ").append(odo).append("\n");
            sb.append("}\n");
            return sb.toString();
        }
        
        @Override public String toString() { return asJSON(); }

        @Override public double getLat() { return lat; }
        @Override public double getLng() { return lng; }
    }

    class KMLExporter {
        private static final String CarIconFileName = "car.png";
        private final String[] pathColors = {
            "ff0000ff",     // Red
            "ff00ff00",     // Green
            "ffff0000",     // Blue
            "ffffff00",     // Cyan
            "ffff00ff",     // Magenta
            "ff0000ff"      // Yellow
        };
        private int pathColorIndex = 0;
        private int indent = 0;
        private PrintWriter pw;
        
        private void emitIndent() {
            for (int i = 0; i < indent; i++) {
                pw.print("    ");
            }
        }
        
        private void println(String s) { emitIndent(); pw.println(s); }
        private void emitOpen(String s) { emitIndent(); pw.println(s); indent++; }
        private void emitClose(String s) { indent--; emitIndent(); pw.println(s); }
        private void format(String s, Object... args) {
            emitIndent();
            pw.format(s, args);
        }
        
        public boolean export(List<Trip> trips, File toFile) {
            File tempDir;
            File kmlFile;
            
            try {
                tempDir = Files.createTempDirectory("VTKML").toFile();
                kmlFile = File.createTempFile("VTKML", ".kml", tempDir);
                pw = new PrintWriter(kmlFile);
                InputStream is =
                        getClass().getClassLoader().getResourceAsStream(CarIconResource);
                File carIconFile = new File(tempDir, CarIconFileName);
                FileUtils.copyInputStreamToFile(is, carIconFile);
                emitKML(trips);
                pw.flush(); pw.close();
                return zipEm(toFile, carIconFile, kmlFile);
            } catch (IOException ex) {
                Tesla.logger.warning("Unable to create KML file or directory");
                return false;
            }
        }
        
        private void emitKML(List<Trip> trips) {
            println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
            emitOpen("<Document>");

            for (Trip t : trips) {
                if (t.firstWayPoint().decoration == null)
                    decorateWayPoints(t);
                emitPath(t);
                emitFolderOfMarkers(t);
            }
            
            emitClose("</Document>");
            println("</kml>");
        }
        
        private void emitCarMarker(WayPoint wp) {
                emitOpen("<Placemark>");
                emitExtendedData(wp);
                emitPoint(wp);
                emitIcon(wp);
                emitClose("</Placemark>");
        }

        private void emitExtendedData(WayPoint wp) {
            emitOpen("<ExtendedData>");
            format("<Data name=\"Time\"><value>%1$tH:%1$tM:%1$tS</value></Data>\n",
                    new Date(wp.timestamp));
            if (wp.decoration != null) {
                for (Map.Entry<String, Double> entry : wp.decoration.entrySet()) {
                    String valueType = entry.getKey();
                    double value = entry.getValue();
                    format("<Data name=\"%s\"><value>%.1f</value></Data>\n",
                            valueType, value);
                }
            }
            emitClose("</ExtendedData>"); 
        }
        
        private void emitFolderOfMarkers(Trip t) {
            emitOpen("<Folder>");
            println("<open>0</open>");
            format( "<name>"+
                       "Tesla Positions on %1$tY-%1$tm-%1$td @ "+
                       "%1$tH:%1$tM</name>\n", new Date(t.firstWayPoint().timestamp));
            for (WayPoint wp : t.waypoints) {
                emitCarMarker(wp);
            }
            emitClose("</Folder>");
        }
        
        private void emitPath(Trip t) {
                emitOpen("<Placemark>");
                format(
                    "<name>Tesla Path on %1$tY-%1$tm-%1$td @ %1$tH:%1$tM</name>\n",
                    new Date(t.firstWayPoint().timestamp));
                emitOpen("<Style>");
                emitOpen("<LineStyle>");
		format("<color>%s</color>\n", pathColors[pathColorIndex++ % pathColors.length]);
		println("<width>3</width>");
                emitClose("</LineStyle>"); 
                emitClose("</Style>"); 
                emitOpen("<LineString>");
                println("<tessellate>1</tessellate>");
                emitOpen("<coordinates>");
                for (WayPoint wp : t.waypoints) {
                    format("%f,%f,0\n", wp.lng, wp.lat);
                }
                emitClose("</coordinates>"); 
                emitClose("</LineString>"); 
                emitClose("</Placemark>"); 
        }
        
        private void emitPoint(WayPoint wp) {
            emitOpen("<Point>");
            format("<coordinates>%f,%f,0</coordinates>\n", wp.lng, wp.lat);
            emitClose("</Point>"); 
        }
        
        private void emitIcon(WayPoint wp) {
            emitOpen("<Style>"); 
            emitOpen("<IconStyle>");
            println("<scale>0.7</scale>");
            format("<heading>%d</heading>\n", wp.heading);
            format("<Icon><href>%s</href></Icon>\n", CarIconFileName);
            emitClose("</IconStyle>"); 
            emitClose("</Style>"); 
        }
        
        private boolean zipEm(File toFile, File... files) {
            try {
                byte[] buffer = new byte[1024];
                FileOutputStream fos = new FileOutputStream(toFile);
                ZipOutputStream zos = new ZipOutputStream(fos);

                for (File file : files) {
                    FileInputStream fis = new FileInputStream(file);
                    zos.putNextEntry(new ZipEntry(file.getName()));

                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }

                    zos.closeEntry();
                    fis.close();
                }
                zos.close();

            } catch (IOException ioe) {
                Tesla.logger.warning("Error creating zip file: " + ioe);
                return false;
            }

            return true;
        }
        
    }
}

