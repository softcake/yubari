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

package org.softcake.yubari.netty;

import com.dukascopy.dds4.transport.authorization.AuthorizationProviderListener;
import java.net.InetSocketAddress;

public abstract class AbstractClientAuthorizationProvider implements ClientAuthorizationProvider {
    protected AuthorizationProviderListener listener;
    protected String userAgent;
    protected boolean secondaryConnectionDisabled;
    protected long droppableMessageServerTTL;
    protected String sessionName;

    public AbstractClientAuthorizationProvider() {
    }

    public String getUserAgent() {
        return this.userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isSecondaryConnectionDisabled() {
        return this.secondaryConnectionDisabled;
    }

    public void setSecondaryConnectionDisabled(boolean secondaryConnectionDisabled) {
        this.secondaryConnectionDisabled = secondaryConnectionDisabled;
    }

    public long getDroppableMessageServerTTL() {
        return this.droppableMessageServerTTL;
    }

    public void setDroppableMessageServerTTL(long droppableMessageServerTTL) {
        this.droppableMessageServerTTL = droppableMessageServerTTL;
    }

    /** @deprecated */
    @Deprecated
    public InetSocketAddress getAddress() {
        return null;
    }

    public AuthorizationProviderListener getListener() {
        return this.listener;
    }

    public void setListener(AuthorizationProviderListener listener) {
        this.listener = listener;
    }

    public void cleanUp() {
        this.listener = null;
    }

    public String getSessionName() {
        return this.sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }
}
