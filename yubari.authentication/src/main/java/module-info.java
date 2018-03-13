/**
 * @author The softcake authors.
 */
module org.softcake.yubari.authentication {
    requires auth.protocol.client;
    requires org.slf4j;
    requires srp6a;
    requires json;
    requires java.desktop;
    requires com.google.common;
    requires org.softcake.yubari.connect;
    requires cherry.core;
    requires transport.common;
    requires org.softcake.yubari.netty;
    requires java.prefs;
    requires msg;

    requires java.management;

    //requires transport.common;
   // requires netty.transport.client;
    //requires transport.common;


}