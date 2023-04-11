package io.deephaven.web.client.api;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import jsinterop.base.Js;
import jsinterop.base.JsPropertyMap;

/**
 * Object to parameterize how a CoreClient should connect to its server.
 */
@JsType(namespace = "dh")
public class ConnectOptions {
    /**
     * Key-value map of http headers to set on each request to the server.
     */
    public JsPropertyMap<String> headers = Js.uncheckedCast(JsPropertyMap.of());

    public ConnectOptions() {

    }

    @JsIgnore
    public ConnectOptions(Object connectOptions) {
        this();
        JsPropertyMap<Object> map = Js.asPropertyMap(connectOptions);
        headers = Js.uncheckedCast(map.getAsAny("headers").asPropertyMap());
    }
}
