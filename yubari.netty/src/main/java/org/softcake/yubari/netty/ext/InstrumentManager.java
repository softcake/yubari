/*
 * Copyright 2018 softcake.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softcake.yubari.netty.ext;

import org.softcake.yubari.netty.client.ITransportClient;
import org.softcake.yubari.netty.client.TransportClient;
import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.DisconnectedEvent;

import com.dukascopy.api.Instrument;
import com.dukascopy.dds3.transport.msg.api.InstrumentPeriodTimeWrapper;
import com.dukascopy.dds3.transport.msg.ddsApi.SubscribeResult;
import com.dukascopy.dds3.transport.msg.feeder.QuoteSubscribeRequestMessage;
import com.dukascopy.dds3.transport.msg.feeder.QuoteSubscriptionResponseMessage;
import com.dukascopy.dds3.transport.msg.feeder.QuoteUnsubscribeRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

public class InstrumentManager implements ClientListener {
    public static final Instrument DEFAULT_SUBSCRIBED_INSTRUMENT;
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentManager.class);
    private static final long RESPONSE_TIMEOUT_SEC = 20L;

    static {
        DEFAULT_SUBSCRIBED_INSTRUMENT = Instrument.EURUSD;
    }

    private final Set<Instrument> subscribedInstruments;
    private final Set<Instrument> fullDepthSubscribedInstruments;
    private final AtomicLong lastSubscriptionTimestamp;
    private TransportClient transport;

    public InstrumentManager(ITransportClient transportClient) {

        this.subscribedInstruments = new TreeSet(Instrument.COMPARATOR);
        this.fullDepthSubscribedInstruments = new TreeSet(Instrument.COMPARATOR);


        this.lastSubscriptionTimestamp = new AtomicLong(-9223372036854775808L);
        if (transportClient == null) {
            throw new IllegalArgumentException("Transport Client is null");
        } else {
            this.transport = (TransportClient) transportClient;

            this.transport.addListener(this);
            this.transport.observeFeedbackMessages().subscribe(new Consumer<ProtocolMessage>() {
                @Override
                public void accept(final ProtocolMessage protocolMessage) throws Exception {
                    feedbackMessageReceived(transport, protocolMessage);
                }
            });
            LOGGER.info("InstrumentManager created.");
        }
    }

    public InstrumentManager() {

        this.subscribedInstruments = new TreeSet(Instrument.COMPARATOR);
        this.fullDepthSubscribedInstruments = new TreeSet(Instrument.COMPARATOR);
        this.lastSubscriptionTimestamp = new AtomicLong(-9223372036854775808L);
    }

    public void feedbackMessageReceived(ITransportClient client, ProtocolMessage message) {

    }

    public void authorized(ITransportClient client) {

    }

    public void disconnected(ITransportClient client, DisconnectedEvent disconnectedEvent) {

        synchronized (this.subscribedInstruments) {
            this.subscribedInstruments.clear();
        }


        synchronized (this.fullDepthSubscribedInstruments) {
            this.fullDepthSubscribedInstruments.clear();
        }
    }

    public void addToSubscribed(Set<Instrument> instruments) {

        this.doSubscribe(instruments, false);
    }

    public void addToSubscribed(Set<Instrument> instruments, boolean waitForTicks) {

        this.doSubscribe(instruments, waitForTicks);
    }

    public void subscribeToFullDepth(Set<Instrument> instruments) {

        this.doSubscribeFullDepth(instruments, false, false);
    }

    public void addToFullDepthSubscribed(Set<Instrument> instruments) {

        this.doSubscribeFullDepth(instruments, false, true);
    }

    public Set<Instrument> getSubscribedInstruments() {

        Set<Instrument> result = new TreeSet<>(Instrument.COMPARATOR);
        synchronized (this.subscribedInstruments) {
            if (this.subscribedInstruments.remove(null)) {
                LOGGER.error(
                    "Instrument Manager contains null element in subscribed instruments set. Check subscription logic"
                    + ".");
            }

            result.addAll(this.subscribedInstruments);
            return result;
        }
    }

    public Set<Instrument> getFullDepthSubscribedInstruments() {

        Set<Instrument> result = new TreeSet<>(Instrument.COMPARATOR);

        synchronized (this.fullDepthSubscribedInstruments) {
            result.addAll(this.fullDepthSubscribedInstruments);
            return result;
        }
    }

    private void doSubscribe(Set<Instrument> instruments, boolean waitForTicks) {

        this.doSubscribe(instruments, false, waitForTicks, false);
    }

    private void doSubscribeFullDepth(Set<Instrument> instruments, boolean waitForTicks, boolean add) {

        this.doSubscribe(instruments, true, waitForTicks, add);
    }

    private void doSubscribe(Set<Instrument> instruments, boolean toFullDepth, boolean waitForTicks, boolean add) {


        if (instruments == null || (!toFullDepth && instruments.isEmpty())) {return;}

        Observable<ProtocolMessage> future = this.performSubscribe(instruments, toFullDepth, add);

        if (future == null) {return;}


        future.subscribe(new Observer<ProtocolMessage>() {
            @Override
            public void onSubscribe(final Disposable d) {

            }

            @Override
            public void onNext(final ProtocolMessage response) {
                String log = "Instrument subscribe request listener fired";
                LOGGER.debug(log);





                    if (response instanceof QuoteSubscriptionResponseMessage) {
                        QuoteSubscriptionResponseMessage
                            quoteSubscriptionResponseMessage
                            = (QuoteSubscriptionResponseMessage) response;
                        handleQuoteSubscriptionResponse(quoteSubscriptionResponseMessage,
                                                        toFullDepth,
                                                        waitForTicks,
                                                        add);
                    } else {
                        LOGGER.error("QuoteSubscribeRequestMessage asynch request failed. Response: {}", response);
                    }
            }

            @Override
            public void onError(final Throwable e) {
                LOGGER.error("Instruments subscribe request to server failed: {}",
                             e.getMessage(),
                             e);
            }

            @Override
            public void onComplete() {

            }
        });



    }

    private Observable<ProtocolMessage> performSubscribe(Set<Instrument> instrumentsToSubscribe,
                                                         boolean subscribeToFullDepth,
                                                         boolean add) {

        Set<Instrument> instruments = new HashSet(instrumentsToSubscribe);
        Set<String> quotes;
        if (!subscribeToFullDepth) {
            instruments.removeAll(this.getSubscribedInstruments());
            if (instruments.isEmpty()) {
                LOGGER.debug("Instruments subscription is skipped due to "
                             + instrumentsToSubscribe
                             + " are already subscribed.");
                return null;
            }
        } else {
            Set<Instrument> subscribedInstruments = this.getFullDepthSubscribedInstruments();
            if (add) {
                instruments.addAll(subscribedInstruments);
            }

            if (subscribedInstruments.equals(instruments)) {
                LOGGER.debug("Full depth subscription is skipped. New instruments set is the same.");
                return null;
            }
        }

        if (this.transport != null && this.transport.isOnline()) {
            quotes = this.toQuotes(instruments);
            if (subscribeToFullDepth && quotes.isEmpty()) {
                LOGGER.info("Unsubscribing from "
                            + this.getFullDepthSubscribedInstruments()
                            + ", full depth: "
                            + subscribeToFullDepth);
            } else {
                LOGGER.info("Subscribing to " + quotes + ", full depth: " + subscribeToFullDepth);
            }


            if (!subscribeToFullDepth) {

                synchronized (this.subscribedInstruments) {
                    this.subscribedInstruments.addAll(instruments);
                }
            } else {

                synchronized (this.fullDepthSubscribedInstruments) {
                    this.fullDepthSubscribedInstruments.clear();
                    this.fullDepthSubscribedInstruments.addAll(instruments);
                }
            }

            boolean needFirstTimes = false;


            QuoteSubscribeRequestMessage req = new QuoteSubscribeRequestMessage();
            req.setTopOfBook(!subscribeToFullDepth);
            req.setInstruments(quotes);
            req.setSubscribeOnSplits(true);
            if (subscribeToFullDepth && !add) {
                Map<String, Boolean> lastTicksConfig = new HashMap();
                Iterator var16 = quotes.iterator();

                while (var16.hasNext()) {
                    String instrumentName = (String) var16.next();
                    lastTicksConfig.put(instrumentName, false);
                }

                req.setLastTicksConfig(lastTicksConfig);
            }

            req.setNeedFirstTimes(needFirstTimes);
            return this.transport.sendRequestAsync(req);
        } else {
            LOGGER.warn("Skip instruments subscription. Client not online");
            return null;
        }
    }

    private Observable<ProtocolMessage> performUnsubscribe(Set<Instrument> instrumentsToUnsubscribe) {

        Set<Instrument> instrmuentsToUnsubscribe = new HashSet();
        Set<Instrument> subscribedInstruments = this.getSubscribedInstruments();
        Iterator var4 = instrumentsToUnsubscribe.iterator();

        while (var4.hasNext()) {
            Instrument instrument = (Instrument) var4.next();
            if (subscribedInstruments.contains(instrument)) {
                instrmuentsToUnsubscribe.add(instrument);
            }
        }

        if (instrmuentsToUnsubscribe.isEmpty()) {
            LOGGER.debug("Nothing to unsubscribe");
            return null;
        } else if (this.transport != null && this.transport.isOnline()) {
            synchronized (subscribedInstruments) {
                subscribedInstruments.removeAll(instrmuentsToUnsubscribe);
            }

            Set<String> quotes = this.toQuotes(instrmuentsToUnsubscribe);
            QuoteUnsubscribeRequestMessage req = new QuoteUnsubscribeRequestMessage();
            req.setInstruments(quotes);
            req.setTopOfBook(true);
            return this.transport.sendRequestAsync(req);
        } else {
            LOGGER.warn("Skip unsubscribe instruments. Client not online");
            return null;
        }
    }

    private Set<String> toQuotes(Set<Instrument> instruments) {

        Set<String> quotes = new HashSet();
        Iterator var3 = instruments.iterator();

        while (var3.hasNext()) {
            Instrument instrument = (Instrument) var3.next();
            if (instrument != null) {
                quotes.add(instrument.toString());
            } else {
                LOGGER.warn(
                    "Attempt to (un)subscribe to set of instruments with null element(s). Check (un)subscription "
                    + "logic.");
            }
        }

        return quotes;
    }

    private void handleQuoteSubscriptionResponse(QuoteSubscriptionResponseMessage response,
                                                 boolean toFullDepth,
                                                 boolean waitForTicks,
                                                 boolean add) {

        this.handleSubscriptionResults(response.getSubscriptionResult(),
                                       response.getTimestamp(),
                                       response.getFirstTimes(),
                                       toFullDepth,
                                       waitForTicks,
                                       add);
    }

    private void handleSubscriptionResults(Map<String, SubscribeResult> subscriptionResult,
                                           long timestamp,
                                           Set<InstrumentPeriodTimeWrapper> firstTimes,
                                           boolean toFullDepth,
                                           boolean waitForTicks,
                                           boolean add) {

        synchronized (this.lastSubscriptionTimestamp) {
            if (this.lastSubscriptionTimestamp.get() > timestamp) {
                LOGGER.warn("Subscription response message is obsolete. Skip handling it.");
                return;
            }

            this.lastSubscriptionTimestamp.set(timestamp);
        }

        Map<Instrument, InstrumentSubscriptionResult> result = new HashMap<>();
        Iterator var9 = subscriptionResult.keySet().iterator();

        while (true) {
            while (var9.hasNext()) {
                String quote = (String) var9.next();
                Instrument instrument = Instrument.fromString(quote);
                if (instrument != null) {
                    SubscribeResult subscribeResult = subscriptionResult.get(quote);
                    InstrumentSubscriptionResult
                        instrumentSubscriptionResult
                        = InstrumentSubscriptionResult.fromSubscribeResult(subscribeResult);
                    if (instrumentSubscriptionResult == null) {
                        LOGGER.error("Instrument subscription result is null for quote: {}", quote);
                    } else {

                        synchronized (this.subscribedInstruments) {
                            if (instrumentSubscriptionResult.isSubscribed()) {
                                this.subscribedInstruments.add(instrument);
                            } else {
                                this.subscribedInstruments.remove(instrument);
                            }
                        }


                        synchronized (this.fullDepthSubscribedInstruments) {
                            if (instrumentSubscriptionResult.isFullDepthSubscribed()) {
                                this.fullDepthSubscribedInstruments.add(instrument);
                            } else {
                                this.fullDepthSubscribedInstruments.remove(instrument);
                            }
                        }

                        result.put(instrument, instrumentSubscriptionResult);
                    }
                } else {
                    LOGGER.warn("Subscribed/Unsubscribed instrument is not available " + quote);
                }
            }


            LOGGER.info("SUBSCRIBED INSTRUMENTS: {}", this.getSubscribedInstruments());
            LOGGER.info("FULL DEPTH INSTRUMENTS: {}", this.getFullDepthSubscribedInstruments());


            return;
        }
    }
}
