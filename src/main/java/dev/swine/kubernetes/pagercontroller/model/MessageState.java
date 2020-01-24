package dev.swine.kubernetes.pagercontroller.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageState {
    NEW,
    SENT;

    @JsonCreator
    public static MessageState forValue(String value) {
        switch(value) {
            case "New":
                return NEW;
            case "" :
                return NEW;
            case "Sent":
                return SENT;
            default:
                return null;
        }
    }

    @JsonValue
    public String toValue() {
        switch (this) {
            case NEW:
                return "New";
            case SENT:
                return "Sent";
            default:
                return null;
        }
    }
}
