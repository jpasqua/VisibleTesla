/*
 * MultiGauge.java - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Apr 19, 2014
 */

package org.noroomattheinn.visibletesla;
//package multigauge;

import javafx.geometry.Side;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.Light.Distant;
import javafx.scene.effect.Lighting;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextBoundsType;

/**
 * A gauge that displays two radial values and gives a textual readout of one.
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class MultiGauge {
    private static final String FontName = "digital-7.ttf";
    private static final String ResourceDir = "org/noroomattheinn/TeslaResources/";
    //private static final String ResourceDir = "multigauge/";
    
    private Pane gaugePane;
    private Group centerArea;
    private Text readout;
    private Side readoutSide = Side.LEFT;
    private Gauge left, right;
    private int radius, thickness;
    
    MultiGauge(int radius) {
        this(radius, 10, 0, 100, 0, 100);
    }

    MultiGauge(int radius, int thickness,
               double leftMin, double leftMax,
               double rightMin, double rightMax) {
        if (thickness > radius) {
            thickness = radius;
        }
        this.radius = radius; this.thickness = thickness;
        
        gaugePane = new Pane();
        gaugePane.setPrefSize(radius * 2, radius * 2);

        left =  new Gauge(Side.LEFT,  radius, leftMin,  leftMax,  Color.DARKGREEN);
        right = new Gauge(Side.RIGHT, radius, rightMin, rightMax, Color.ORANGERED);

        Circle centerCircle = new Circle(radius, radius, radius - thickness);
        centerCircle.setFill(Color.web("#404040"));        
        Lighting l = new Lighting(new Distant(-135.0f, 2*radius, Color.WHITE));
        l.setSurfaceScale(5.0f);
        centerCircle.setEffect(l);
        
        Circle fullCircle = new Circle(radius, radius, radius);
        fullCircle.setFill(Color.web("#f0f0f0"));
        fullCircle.setStroke(Color.web("#d0d0d0"));

        readout = new Text("000");
        Font df = Font.loadFont(getClass().getClassLoader().getResource(
            ResourceDir+FontName).toExternalForm(), 17);
        readout.setFont(df);
        //readout.setFont(Font.font("LucidaConsole", FontWeight.BOLD, 14));
        //readout.setFont(Font.font("Digital-7", 17));
        readout.setFontSmoothingType(FontSmoothingType.LCD);
        readout.setFill(Color.web("#fff"));
        readout.setTextAlignment(TextAlignment.CENTER);

        // Center the text in the display
        readout.setBoundsType(TextBoundsType.VISUAL);
        double w = readout.getBoundsInLocal().getWidth();
        double h = readout.getBoundsInLocal().getHeight();
        readout.relocate(radius - w/2, radius - h/2);
        readout.setWrappingWidth(w + 4);

        readout.setEffect(genTextEffect());

        centerArea = new Group(centerCircle, readout);

        gaugePane.getChildren().addAll(fullCircle, left.getNode(), right.getNode(), centerArea);
    }

    Node getContainer() { return gaugePane; }

    void setVal(Side s, double val) {
        getGauge(s).setVal(val);
        if (readoutSide == s) {
            readout.setText(String.valueOf((int) val));
        }
    }

    void setRange(Side s, double min, double max) { }
    void setPaint(Side s, Paint paint, Paint altPaint) {
        getGauge(s).setPaint(paint, altPaint);
    }
    
    void useGradient(Side s, Color baseColor, Color altBaseColor) {
        getGauge(s).setPaint(gradientFromBase(baseColor), gradientFromBase(altBaseColor));
    }
    
    private RadialGradient gradientFromBase(Color c) {
        return new RadialGradient(
                0, 0, radius, radius, radius, false, CycleMethod.NO_CYCLE,
                new Stop[]{
                    new Stop(0.00, trans(c, 0.0)),
                    new Stop((radius-thickness)/radius, trans(c, 0.3)),
                    new Stop(0.85, trans(c, 0.5)),
                    new Stop(1.00, c)
        });
    } 
    
    private Color trans(Color c, double opacity) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), opacity);
    }
    
    Side getReadoutSide() { return readoutSide; }
    void setReadoutSide(Side s) { readoutSide = s == Side.LEFT ? Side.LEFT : Side.RIGHT; }

    private Gauge getGauge(Side s) { return s == Side.LEFT ? left : right; }
    
    private Effect genTextEffect() {
        return new DropShadow(2, 0, 0, Color.BLUE);
    }
}

class Gauge {
    private int direction;
    private double min, max;
    private Arc arc;
    private Paint paint, altPaint;
    private Side side;
    
    Gauge(Side side, int radius, double min, double max, Paint p) {
        this.side = side;
        this.direction = side == Side.LEFT ? -1 : 1;
        this.min = min;
        this.max = max;
        arc = new Arc(radius, radius, radius, radius, 270, 0);
        arc.setType(ArcType.ROUND);
        arc.setFill(p);
        paint = p;
        altPaint = p;
        
        // Ok, if min < 0 and max > 0 then we do something special. We put
        // 0.0 at the midpoint and paint up for positive values and down for
        // nragtive values
        if (max > 0 && min < 0) {
            arc.setStartAngle(side == Side.LEFT? 180 : 0);
        }
    }

    Node getNode() { return arc; }

    void setRange(double min, double max) {
        this.min = min; this.max = max;
        if (max > 0 && min < 0) {
            arc.setStartAngle(side == Side.LEFT? 180 : 0);
        }
    }
    
    void setPaint(Paint mainPaint, Paint altPaint) {
        this.paint = mainPaint;
        this.altPaint = altPaint;
    }
    
    void setVal(double val) {
        val = Math.max(min, Math.min(max, val));
        // Ok, if min < 0 and max > 0 then we do something special. We put
        // 0.0 at the midpoint and paint up for positive values and down for
        // nragtive values
        if (max > 0 && min < 0) {
            if (val < 0) {
                double percent = val / min;
                arc.setFill(altPaint);
                arc.setLength(90 * percent * direction * -1);
            } else {
                arc.setFill(paint);
                double percent = val / (max);
                arc.setLength(90 * percent * direction);
            }
        } else {
            double percent = val / (max-min);
            arc.setFill(paint);
            arc.setLength(180 * percent * direction);
        }
    }

}