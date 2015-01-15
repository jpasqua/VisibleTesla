/*
 * GenericProperty - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 29, 2013
 */

package org.noroomattheinn.visibletesla;

import javafx.beans.property.SimpleStringProperty;

/**
 * Represents a row in a table with columns: name, value, and units.
 * Must be public to enable use by JavaFX reflection capabilities.
 * 
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
public class GenericProperty {
    private final SimpleStringProperty name;
    private final SimpleStringProperty value;
    private final SimpleStringProperty units;

    public GenericProperty(String name, String value, String units) {
        this.name = new SimpleStringProperty(name);
        this.value = new SimpleStringProperty(value);
        this.units = new SimpleStringProperty(units);
    }

    public SimpleStringProperty nameProperty() { return name; }
    public SimpleStringProperty valueProperty() { return value; }
    public SimpleStringProperty unitsProperty() { return units; }

    public String getName() { return name.get(); }
    public void setName(String newName) { name.set(newName); }

    public String getValue() { return value.get(); }
    public void setValue(String newValue) { value.set(newValue); }

    public String getUnits() { return units.get(); }
    public void setUnits(String newUnits) { units.set(newUnits); }

}
