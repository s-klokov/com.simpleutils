module com.simpleutils {
    requires json.simple;
    requires java.desktop;
    requires java.mail;
    requires activation;
    exports com.simpleutils;
    exports com.simpleutils.json;
    exports com.simpleutils.logs;
    exports com.simpleutils.quik;
    exports com.simpleutils.socket;
    exports com.simpleutils.quik.requests;
    exports com.simpleutils.email;
}