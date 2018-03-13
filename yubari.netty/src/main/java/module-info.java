/**
 * @author The softcake authors
 */
module org.softcake.yubari.netty {
    requires auth.protocol.client;
    requires org.slf4j;
    requires srp6a;
    requires json;
    requires java.desktop;
    requires com.google.common;
    requires org.softcake.yubari.connect;
    requires cherry.core;
    requires transport.common;

    requires java.prefs;
    requires msg;

    requires java.management;
    requires netty.transport;
    requires netty.codec;
    requires netty.handler;
    requires netty.common;
    requires netty.buffer;
    exports org.softcake.yubari.netty;
    //requires transport.common;
    // requires netty.transport.client;
    //requires transport.common;
}
