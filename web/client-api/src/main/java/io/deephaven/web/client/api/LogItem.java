/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.web.client.api;

import com.vertispan.tsdefs.annotations.TsInterface;
import com.vertispan.tsdefs.annotations.TsName;
import jsinterop.annotations.JsProperty;

import java.io.Serializable;

/**
 * A log entry sent from the server.
 */
@TsInterface
@TsName(namespace = "dh.ide")
public class LogItem implements Serializable {

    private double micros;

    private String logLevel;

    private String message;

    /**
     * @return Timestamp of the message in microseconds since Jan 1, 1970 UTC.
     */
    @JsProperty
    public double getMicros() {
        return micros;
    }

    public void setMicros(double timestamp) {
        this.micros = timestamp;
    }

    /**
     * @return The level of the log message, enabling the client to ignore messages.
     */
    @JsProperty
    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * @return The log message written on the server.
     */
    @JsProperty
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
