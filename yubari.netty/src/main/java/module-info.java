/**
 * @author The softcake authors
 */
module org.softcake.yubari.netty {
    requires auth.protocol.client;
    requires org.slf4j;
    requires srp6a;
    requires json;
    requires java.desktop;
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
    requires io.netty.buffer;
    requires java.rmi;
    requires JForex.API;
    requires com.google.common;
    requires io.reactivex.rxjava2;
    exports org.softcake.yubari.netty;
    exports org.softcake.yubari.netty.mina;
    exports org.softcake.yubari.netty.stream;
    exports org.softcake.yubari.netty.client;
    exports org.softcake.yubari.netty.authorization;
    exports org.softcake.yubari.netty.ext;
}
