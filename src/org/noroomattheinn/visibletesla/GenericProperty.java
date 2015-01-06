/*
 * GenericProperty - Copyright(c) 2013 Joe Pasqua
 * Provided under the MIT License. See the LICENSE file for details.
 * Created: Nov 29, 2013
 */

package org.noroomattheinn.visibletesla;

import javafx.beans.property.SimpleStringProperty;

/**
 *
 * @author Joe Pasqua <joe at NoRoomAtTheInn dot org>
 */
class GenericProperty {
    private final SimpleStringProperty name;
    private final SimpleStringProperty value;
    private final SimpleStringProperty units;

    GenericProperty(String name, String value, String units) {
        this.name = new SimpleStringProperty(name);
        this.value = new SimpleStringProperty(value);
        this.units = new SimpleStringProperty(units);
    }

    SimpleStringProperty nameProperty() { return name; }
    SimpleStringProperty valueProperty() { return value; }
    SimpleStringProperty unitsProperty() { return units; }

    String getName() { return name.get(); }
    void setName(String newName) { name.set(newName); }

    String getValue() { return value.get(); }
    void setValue(String newValue) { value.set(newValue); }

    String getUnits() { return units.get(); }
    void setUnits(String newUnits) { units.set(newUnits); }

}
