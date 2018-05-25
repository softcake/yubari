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
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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

    public InstrumentManager(final ITransportClient transportClient) {

        this.subscribedInstruments = new TreeSet<>(Instrument.COMPARATOR);
        this.fullDepthSubscribedInstruments = new TreeSet<>(Instrument.COMPARATOR);


        this.lastSubscriptionTimestamp = new AtomicLong(-9223372036854775808L);
        if (transportClient == null) {
            throw new IllegalArgumentException("Transport Client is null");
        } else {
            this.transport = (TransportClient) transportClient;

            this.transport.addListener(this);
            this.transport.observeMessagesReceived()
                          .subscribe(new Consumer<ProtocolMessage>() {
                              @Override
                              public void accept(final ProtocolMessage protocolMessage) throws Exception {

                                  feedbackMessageReceived(transport, protocolMessage);
                              }
                          });
            LOGGER.info("InstrumentManager created.");
        }
    }

    public InstrumentManager() {

        this.subscribedInstruments = new TreeSet<>(Instrument.COMPARATOR);
        this.fullDepthSubscribedInstruments = new TreeSet<>(Instrument.COMPARATOR);
        this.lastSubscriptionTimestamp = new AtomicLong(-9223372036854775808L);
    }

    public void feedbackMessageReceived(final ITransportClient client, final ProtocolMessage message) {

    }

    public void authorized(final ITransportClient client) {

    }

    public void disconnected(final ITransportClient client, final DisconnectedEvent disconnectedEvent) {

        synchronized (this.subscribedInstruments) {
            this.subscribedInstruments.clear();
        }


        synchronized (this.fullDepthSubscribedInstruments) {
            this.fullDepthSubscribedInstruments.clear();
        }
    }

    public void addToSubscribed(final Set<Instrument> instruments) {

        this.doSubscribe(instruments, false);
    }

    public void addToSubscribed(final Set<Instrument> instruments, final boolean waitForTicks) {

        this.doSubscribe(instruments, waitForTicks);
    }

    public void subscribeToFullDepth(final Set<Instrument> instruments) {

        this.doSubscribeFullDepth(instruments, false, false);
    }

    public void addToFullDepthSubscribed(final Set<Instrument> instruments) {

        this.doSubscribeFullDepth(instruments, false, true);
    }

    public Set<Instrument> getSubscribedInstruments() {

        final Set<Instrument> result = new TreeSet<>(Instrument.COMPARATOR);
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

        final Set<Instrument> result = new TreeSet<>(Instrument.COMPARATOR);

        synchronized (this.fullDepthSubscribedInstruments) {
            result.addAll(this.fullDepthSubscribedInstruments);
            return result;
        }
    }

    private void doSubscribe(final Set<Instrument> instruments, final boolean waitForTicks) {

        this.doSubscribe(instruments, false, waitForTicks, false);
    }

    private void doSubscribeFullDepth(final Set<Instrument> instruments,
                                      final boolean waitForTicks,
                                      final boolean add) {

        this.doSubscribe(instruments, true, waitForTicks, add);
    }

    private void doSubscribe(final Set<Instrument> instruments,
                             final boolean toFullDepth,
                             final boolean waitForTicks,
                             final boolean add) {


        if (instruments == null || (!toFullDepth && instruments.isEmpty())) {return;}

        final Single<ProtocolMessage> future = this.performSubscribe(instruments, toFullDepth, add);

        if (future == null) {return;}


        future.subscribe(new SingleObserver<ProtocolMessage>() {
            @Override
            public void onSubscribe(final Disposable d) {

            }

            @Override
            public void onSuccess(final ProtocolMessage response) {

                final String log = "Instrument subscribe request listener fired";
                LOGGER.debug(log);


                if (response instanceof QuoteSubscriptionResponseMessage) {
                    final QuoteSubscriptionResponseMessage
                        quoteSubscriptionResponseMessage
                        = (QuoteSubscriptionResponseMessage) response;
                    handleQuoteSubscriptionResponse(quoteSubscriptionResponseMessage, toFullDepth, waitForTicks, add);
                } else {
                    LOGGER.error("QuoteSubscribeRequestMessage asynch request failed. Response: {}", response);
                }
            }

            @Override
            public void onError(final Throwable e) {

                LOGGER.error("Instruments subscribe request to server failed: {}", e.getMessage(), e);
            }
        });


    }

    private Single<ProtocolMessage> performSubscribe(final Set<Instrument> instrumentsToSubscribe,
                                                     final boolean subscribeToFullDepth,
                                                     final boolean add) {

        final Set<Instrument> instruments = new HashSet<>(instrumentsToSubscribe);
        final Set<String> quotes;
        if (!subscribeToFullDepth) {
            instruments.removeAll(this.getSubscribedInstruments());
            if (instruments.isEmpty()) {
                LOGGER.debug("Instruments subscription is skipped due to "
                             + instrumentsToSubscribe
                             + " are already subscribed.");
                return null;
            }
        } else {
            final Set<Instrument> subscribedInstruments = this.getFullDepthSubscribedInstruments();
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

            final boolean needFirstTimes = false;


            final QuoteSubscribeRequestMessage req = new QuoteSubscribeRequestMessage();
            req.setTopOfBook(!subscribeToFullDepth);
            req.setInstruments(quotes);
            req.setSubscribeOnSplits(true);
            if (subscribeToFullDepth && !add) {
                final Map<String, Boolean> lastTicksConfig = new HashMap<>();
                final Iterator<String> var16 = quotes.iterator();

                while (var16.hasNext()) {
                    final String instrumentName = var16.next();
                    lastTicksConfig.put(instrumentName, false);
                }

                req.setLastTicksConfig(lastTicksConfig);
            }

            req.setNeedFirstTimes(needFirstTimes);
            return this.transport.sendRequestAsync(req);
        } else {
            LOGGER.warn("Skip instruments subscription. Client not onOnline");
            return null;
        }
    }

    private Single<ProtocolMessage> performUnsubscribe(final Set<Instrument> instrumentsToUnsubscribe) {

        final Set<Instrument> instrmuentsToUnsubscribe = new HashSet<>();
        final Set<Instrument> subscribedInstruments = this.getSubscribedInstruments();
        final Iterator<Instrument> var4 = instrumentsToUnsubscribe.iterator();

        while (var4.hasNext()) {
            final Instrument instrument = var4.next();
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

            final Set<String> quotes = this.toQuotes(instrmuentsToUnsubscribe);
            final QuoteUnsubscribeRequestMessage req = new QuoteUnsubscribeRequestMessage();
            req.setInstruments(quotes);
            req.setTopOfBook(true);
            return this.transport.sendRequestAsync(req);
        } else {
            LOGGER.warn("Skip unsubscribe instruments. Client not onOnline");
            return null;
        }
    }

    private Set<String> toQuotes(final Set<Instrument> instruments) {

        // final Set<String> quotes = new HashSet<>();

        return instruments.stream()
                          .filter(Objects::nonNull)
                          .map(Instrument::toString)
                          .collect(Collectors.toSet());

       /* final Iterator var3 = instruments.iterator();

        while (var3.hasNext()) {
            final Instrument instrument = (Instrument) var3.next();
            if (instrument != null) {
                quotes.add(instrument.toString());
            } else {
                LOGGER.warn(
                    "Attempt to (un)subscribe to set of instruments with null element(s). Check (un)subscription "
                    + "logic.");
            }
        }*/

        //return quotes;
    }

    private void handleQuoteSubscriptionResponse(final QuoteSubscriptionResponseMessage response,
                                                 final boolean toFullDepth,
                                                 final boolean waitForTicks,
                                                 final boolean add) {

        this.handleSubscriptionResults(response.getSubscriptionResult(),
                                       response.getTimestamp(),
                                       response.getFirstTimes(),
                                       toFullDepth,
                                       waitForTicks,
                                       add);
    }

    private void handleSubscriptionResults(final Map<String, SubscribeResult> subscriptionResult,
                                           final long timestamp,
                                           final Set<InstrumentPeriodTimeWrapper> firstTimes,
                                           final boolean toFullDepth,
                                           final boolean waitForTicks,
                                           final boolean add) {

        synchronized (this.lastSubscriptionTimestamp) {
            if (this.lastSubscriptionTimestamp.get() > timestamp) {
                LOGGER.warn("Subscription response message is obsolete. Skip handling it.");
                return;
            }

            this.lastSubscriptionTimestamp.set(timestamp);
        }

        final Map<Instrument, InstrumentSubscriptionResult> result = new HashMap<>();
        final Iterator<String> var9 = subscriptionResult.keySet()
                                                            .iterator();

        while (true) {
            while (var9.hasNext()) {
                final String quote = var9.next();
                final Instrument instrument = Instrument.fromString(quote);
                if (instrument != null) {
                    final SubscribeResult subscribeResult = subscriptionResult.get(quote);
                    final InstrumentSubscriptionResult
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
