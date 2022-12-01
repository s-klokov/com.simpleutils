module com.simpleutils {
    requires json.simple;
    requires java.desktop;
    exports com.simpleutils;
    exports com.simpleutils.json;
    exports com.simpleutils.logs;
    exports com.simpleutils.quik;
    exports com.simpleutils.socket;
    exports com.simpleutils.quik.requests;
}