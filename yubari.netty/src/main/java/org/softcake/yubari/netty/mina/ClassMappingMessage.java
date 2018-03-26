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

package org.softcake.yubari.netty.mina;

import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.common.protocol.binary.ClassMappingDirectInvocationHandler;
import com.dukascopy.dds4.transport.common.protocol.binary.CompatibilityTranslator;
import com.dukascopy.dds4.transport.common.protocol.binary.DirectHandler;
import com.dukascopy.dds4.transport.common.protocol.binary.DirectInvocationHandler;
import com.dukascopy.dds4.transport.common.protocol.binary.EncodingContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@DirectHandler(ClassMappingDirectInvocationHandler.class)
public class ClassMappingMessage extends BinaryProtocolMessage {
    private static final long serialVersionUID = -9136331530209801898L;
    private Set<CompatibilityTranslator> messageTranslations = new HashSet();
    private Map<Short, String> classMapping = new HashMap();

    public ClassMappingMessage(EncodingContext encodingContext) {
        Map<Short, Class> mapping = encodingContext.getClassesToMap();
        if (mapping != null) {
            Iterator i$ = mapping.entrySet().iterator();

            while(i$.hasNext()) {
                Entry<Short, Class> e = (Entry)i$.next();
                this.classMapping.put(e.getKey(), ((Class)e.getValue()).getName());
            }
        }

        Map<Class, DirectInvocationHandler> handlers = encodingContext.getClassDirectInvocationHandlers();
        if (handlers != null) {
            Iterator i$ = handlers.entrySet().iterator();

            while(i$.hasNext()) {
                Entry<Class, DirectInvocationHandler> e = (Entry)i$.next();
                CompatibilityTranslator mt = new CompatibilityTranslator((DirectInvocationHandler)e.getValue(), (Class)e.getKey());
                this.messageTranslations.add(mt);
            }
        }

    }

    public ClassMappingMessage() {
    }

    public ClassMappingMessage(BinaryProtocolMessage mesasage) {
        super(mesasage);
    }

    protected Set<CompatibilityTranslator> getMessageTranslations() {
        return this.messageTranslations;
    }

    protected void setMessageTranslations(Set<CompatibilityTranslator> messageTranslations) {
        this.messageTranslations = messageTranslations;
    }

    public Map<Short, String> getClassMapping() {
        return this.classMapping;
    }

    public void setClassMapping(Map<Short, String> classMapping) {
        this.classMapping = classMapping;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("<ClassMappingMessage(");
        if (this.classMapping != null) {
            sb.append("classMapping");
            sb.append("=");
            sb.append(objectToString(this.classMapping));
        }

        if (this.messageTranslations != null) {
            sb.append(",");
            sb.append("messageTranslations");
            sb.append("=");
            sb.append(objectToString(this.messageTranslations));
        }

        sb.append(")>");
        return sb.toString();
    }
}
