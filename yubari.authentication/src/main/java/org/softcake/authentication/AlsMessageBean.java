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

package org.softcake.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlsMessageBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlsMessageBean.class);
    private RemoteLogMessageType remoteLogMessageType;
    private String platformType;
    private String message;
    private String translationKey;
    private IAlsTranslationKey alsTranslationKey;
    private List<Map<String, String>> messageParameters = new ArrayList();
    private boolean doNotSend = false;

    public AlsMessageBean() {

    }

    public AlsMessageBean(RemoteLogMessageType remoteLogMessageType, String translationKey) {
        this.remoteLogMessageType = remoteLogMessageType;
        this.translationKey = translationKey;
    }

    public AlsMessageBean(RemoteLogMessageType remoteLogMessageType, IAlsTranslationKey alsTranslationKey) {
        this.remoteLogMessageType = remoteLogMessageType;
        this.translationKey = alsTranslationKey.getTranslationKey();
        this.alsTranslationKey = alsTranslationKey;
    }

    public AlsMessageBean addParameter(String parameterName, long parameterValue) {
        return this.addParameter(parameterName, String.valueOf(parameterValue));
    }

    public AlsMessageBean addParameter(String parameterName, int parameterValue) {
        return this.addParameter(parameterName, String.valueOf(parameterValue));
    }

    public AlsMessageBean addParameter(String parameterName, String parameterValue) {
        Map<String, String> map = new HashMap();
        map.put(parameterName, parameterValue);
        this.messageParameters.add(map);
        return this;
    }

    public List<Map<String, String>> getMessageParameters() {
        return this.messageParameters;
    }

    public RemoteLogMessageType getRemoteLogMessageType() {
        return this.remoteLogMessageType;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public String getMessage() {
        return this.message;
    }

    public IAlsTranslationKey getAlsTranslationKey() {
        return this.alsTranslationKey;
    }

    public boolean isSendToClientLog() {
        boolean sendToClientLog = true;
        if (this.alsTranslationKey != null) {
            sendToClientLog = this.alsTranslationKey.isSendToClientLog();
        }

        return sendToClientLog;
    }

    public AlsMessageBean setMessage(String message) {
        this.message = message;
        return this;
    }

    public AlsMessageBean setMessage(String message, Object... arguments) {
        try {
            this.message = MessageFormat.format(message, arguments);
        } catch (Throwable var4) {
            this.message = message;
            LOGGER.error(var4.getMessage(), var4);
        }

        return this;
    }

    public AlsMessageBean setMessage(IAlsTranslationKey translationKey) {
        this.message = translationKey.getPlainMessage();
        return this;
    }

    public AlsMessageBean setMessage(IAlsTranslationKey translationKey, Object... arguments) {
        try {
            this.message = MessageFormat.format(translationKey.getPlainMessage(), arguments);
        } catch (Exception var4) {
            this.message = translationKey.getPlainMessage();
            LOGGER.error(var4.getMessage(), var4);
        }

        return this;
    }

    public String getPlatformType() {
        return this.platformType;
    }

    public AlsMessageBean setPlatformType(String platformType) {
        this.platformType = platformType;
        return this;
    }

    public void deleteParameters() {
        this.messageParameters.clear();
    }

    public boolean isDoNotSend() {
        return this.doNotSend;
    }

    public void setDoNotSend(boolean doNotSend) {
        this.doNotSend = doNotSend;
    }
}
