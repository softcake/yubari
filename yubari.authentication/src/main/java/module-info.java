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
}