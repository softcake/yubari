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
requires java.sql;
    requires java.prefs;
    requires msg;

    requires java.management;
    requires io.netty.transport;
    requires io.netty.common;
    requires io.netty.codec;
    requires io.netty.handler;

    exports org.softcake.yubari.netty;
    //requires transport.common;
    // requires netty.transport.client;
    //requires transport.common;
}
