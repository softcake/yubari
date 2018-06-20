/**
 * @author René Neubert
 */module org.softcake.yubari.loginform {
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
    requires JForex.API;
    requires java.sql;
    requires io.reactivex.rxjava2;
    requires java.jnlp;
    requires commons.lang3;

    exports org.softcake.yubari.loginform.controller;
    exports org.softcake.yubari.loginform.resources;
    exports org.softcake.yubari.loginform.service;
}
