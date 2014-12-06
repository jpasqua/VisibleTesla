/*
 * KMLExporter.java - Copyright(c) 2013, 2014 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 27, 2013
 */
package org.noroomattheinn.visibletesla;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.FileUtils;
import static org.noroomattheinn.tesla.Tesla.logger;

/**
 * KMLExporter: Export a Trip to a KML file
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */

class KMLExporter {
    private static final String CarIconFileName = "car.png";
    private static final String CarIconResource = "org/noroomattheinn/TeslaResources/02_loc_arrow@2x.png";
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

    public boolean export(List<TripController.Trip> trips, File toFile) {
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
            logger.warning("Unable to create KML file or directory");
            return false;
        }
    }

    private void emitKML(List<TripController.Trip> trips) {
        println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
        emitOpen("<Document>");

        for (TripController.Trip t : trips) {
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
                new Date(wp.getTime()));
        format("<Data name=\"Power\"><value>%.1f</value></Data>\n",
                wp.get(StatsCollector.PowerKey));
        format("<Data name=\"SOC\"><value>%.1f</value></Data>\n",
                wp.get(StatsCollector.SOCKey));

        emitClose("</ExtendedData>"); 
    }

    private void emitFolderOfMarkers(TripController.Trip t) {
        emitOpen("<Folder>");
        println("<open>0</open>");
        format( "<name>"+
                   "Tesla Positions on %1$tY-%1$tm-%1$td @ "+
                   "%1$tH:%1$tM</name>\n", new Date(t.firstWayPoint().getTime()));
        for (WayPoint wp : t.getWayPoints()) {
            emitCarMarker(wp);
        }
        emitClose("</Folder>");
    }

    private void emitPath(TripController.Trip t) {
            emitOpen("<Placemark>");
            format(
                "<name>Tesla Path on %1$tY-%1$tm-%1$td @ %1$tH:%1$tM</name>\n",
                new Date(t.firstWayPoint().getTime()));
            emitOpen("<Style>");
            emitOpen("<LineStyle>");
            format("<color>%s</color>\n", pathColors[pathColorIndex++ % pathColors.length]);
            println("<width>3</width>");
            emitClose("</LineStyle>"); 
            emitClose("</Style>"); 
            emitOpen("<LineString>");
            println("<tessellate>1</tessellate>");
            emitOpen("<coordinates>");
            for (WayPoint wp : t.getWayPoints()) {
                format("%f,%f,0\n", wp.getLng(), wp.getLat());
            }
            emitClose("</coordinates>"); 
            emitClose("</LineString>"); 
            emitClose("</Placemark>"); 
    }

    private void emitPoint(WayPoint wp) {
        emitOpen("<Point>");
        format("<coordinates>%f,%f,0</coordinates>\n", wp.getLng(), wp.getLat());
        emitClose("</Point>"); 
    }

    private void emitIcon(WayPoint wp) {
        emitOpen("<Style>"); 
        emitOpen("<IconStyle>");
        println("<scale>0.7</scale>");
        format("<heading>%f</heading>\n", wp.get(StatsCollector.HeadingKey));
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
            logger.warning("Error creating zip file: " + ioe);
            return false;
        }

        return true;
    }

}