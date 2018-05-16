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

package org.softcake.yubari.netty.client;

import com.dukascopy.dds3.transport.msg.dfs.DFHistoryChangedMessage;
import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author René Neubert
 */
public class MessageCreator implements Runnable {
    RxPublishExample example;
    int count = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageCreator.class);

    public MessageCreator(final RxPublishExample example) {

        this.example = example;
    }

    @SuppressWarnings("squid:S2189")
    @Override
    public void run() {

        while (true) {




            if (count == 132) {
                count = 0;
                break;
            }


            final long nextLong = ThreadLocalRandom.current().nextLong(1000, 2000);
            try {
               Thread.sleep(nextLong);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
            try {

                //sendMessage("EUR", "USD");
                sendMessageNotDroppable("EUR","USD");
                count++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void sendMessage(String primary, String secondary) throws Exception {

        final CurrencyMarket currencyMarket = new CurrencyMarket();
        currencyMarket.setCreationTimestamp(System.currentTimeMillis());
        currencyMarket.setInstrumentPrimary(primary);
        currencyMarket.setInstrumentSecondary(secondary);
        this.example.messageReceived(currencyMarket);
    }

    private void sendMessageNotDroppable(String primary, String secondary) throws Exception {

        final DFHistoryChangedMessage currencyMarket = new DFHistoryChangedMessage();
        currencyMarket.setTimestamp(System.currentTimeMillis());
        currencyMarket.setInstrument(primary + "/"+secondary);
        if (count >= 130 ) {
            LOGGER.info("latest send message: {}", currencyMarket);
        } else if(count <= 2){
            LOGGER.info("first send message: {}", currencyMarket);
        }

        this.example.messageReceived(currencyMarket);
    }
}
