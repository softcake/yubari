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

import com.dukascopy.api.DataType;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.PriceRange;
import com.dukascopy.api.ReversalAmount;
import com.dukascopy.api.TickBarSize;
import com.dukascopy.api.Unit;
import com.dukascopy.api.feed.FeedDescriptor;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.dds3.transport.msg.news.NewsSource;
import com.dukascopy.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class TransportHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransportHelper.class);
    private static final Class<?>[] REMOTELY_SUPPORTED_TYPES;

    private TransportHelper() {
    }

    public static String valueToString(Object value, Class<?> type) {
        if (value == null) {
            return null;
        } else if (type.equals(Boolean.TYPE)) {
            return Boolean.toString((Boolean)value);
        } else if (type.equals(Boolean.class)) {
            return Boolean.toString((Boolean)value);
        } else if (type.equals(String.class)) {
            return value.toString();
        } else if (type.equals(Integer.class)) {
            return Integer.toString((Integer)value);
        } else if (type.equals(Integer.TYPE)) {
            return Integer.toString((Integer)value);
        } else if (type.equals(Long.class)) {
            return Long.toString((Long)value);
        } else if (type.equals(Long.TYPE)) {
            return Long.toString((Long)value);
        } else if (type.equals(Double.class)) {
            return Double.toString((Double)value);
        } else if (type.equals(Double.TYPE)) {
            return Double.toString((Double)value);
        } else {
            long time;
            if (value instanceof Date) {
                time = ((Date)value).getTime();
                return Long.toString(time);
            } else if (value instanceof Calendar) {
                time = ((Calendar)value).getTimeInMillis();
                return Long.toString(time);
            } else if (value instanceof Period) {
                return ((Period)value).name();
            } else if (value instanceof Instrument) {
                return ((Instrument)value).name();
            } else if (value instanceof Enum) {
                return ((Enum)value).name();
            } else if (value instanceof CollectionParameterWrapper && type.isEnum()) {
                String result = null;
                CollectionParameterWrapper wrapper = CollectionParameterWrapper.class.cast(value);
                if (wrapper.getSelectedValue() != null) {
                    result = ((Enum)Enum.class.cast(wrapper.getSelectedValue())).name();
                }

                return result;
            } else if (value instanceof File) {
                File file = (File)value;
                return file.getAbsolutePath();
            } else if (value instanceof Color) {
                Color color = (Color)value;
                return String.valueOf(color.getRGB());
            } else if (value instanceof TickBarSize) {
                TickBarSize object = (TickBarSize)value;
                return String.valueOf(object.getSize());
            } else if (value instanceof PriceRange) {
                PriceRange object = (PriceRange)value;
                return String.valueOf(object.getPipCount());
            } else if (value instanceof ReversalAmount) {
                ReversalAmount object = (ReversalAmount)value;
                return String.valueOf(object.getAmount());
            } else if (value instanceof FeedDescriptor) {
                FeedDescriptor object = (FeedDescriptor)value;
                return String.valueOf(object.toString());
            } else if (!(value instanceof List) && !(value instanceof Set)) {
                if (value instanceof TimeUnit) {
                    TimeUnit timeUnit = (TimeUnit)value;
                    return timeUnit.name();
                } else {
                    LOGGER.error("Unsupported type : {}", type);
                    return null;
                }
            } else {
                Iterator iterator;
                if (value instanceof List) {
                    iterator = ((List)value).iterator();
                } else {
                    iterator = ((Set)value).iterator();
                }

                JSONArray jsonArray = new JSONArray();

                while(iterator.hasNext()) {
                    Object element = iterator.next();
                    String elementAsString = valueToString(element, element.getClass());
                    if (elementAsString != null) {
                        jsonArray.put(elementAsString);
                        jsonArray.put(element.getClass().getName());
                    }
                }

                return jsonArray.toString();
            }
        }
    }

    public static Object stringToValue(String string, Class<?> type) {
        if (type.equals(String.class)) {
            return string;
        } else if (string != null && string.length() >= 1) {
            try {
                if (type.equals(Boolean.TYPE)) {
                    return Boolean.parseBoolean(string);
                } else if (type.equals(Boolean.class)) {
                    return Boolean.valueOf(string);
                } else if (type.equals(Integer.class)) {
                    return Integer.valueOf(string);
                } else if (type.equals(Integer.TYPE)) {
                    return Integer.valueOf(string);
                } else if (type.equals(Long.class)) {
                    return Long.valueOf(string);
                } else if (type.equals(Long.TYPE)) {
                    return Long.valueOf(string);
                } else if (type.equals(Double.class)) {
                    return Double.valueOf(string);
                } else if (type.equals(Double.TYPE)) {
                    return Double.valueOf(string);
                } else {
                    long millis;
                    if (type.equals(Date.class)) {
                        millis = Long.valueOf(string);
                        return new Date(millis);
                    } else if (Calendar.class.isAssignableFrom(type)) {
                        millis = Long.valueOf(string);
                        Calendar value = Calendar.getInstance();
                        value.setTimeInMillis(millis);
                        return value;
                    } else if (type.equals(Period.class)) {
                        return Period.valueOf(string);
                    } else if (type.equals(Instrument.class)) {
                        return Instrument.valueOf(string);
                    } else if (type.equals(OfferSide.class)) {
                        return OfferSide.valueOf(string);
                    } else if (type.equals(Filter.class)) {
                        return Filter.valueOf(string);
                    } else if (type.equals(Unit.class)) {
                        return Unit.valueOf(string);
                    } else if (type.equals(DataType.class)) {
                        return DataType.valueOf(string);
                    } else if (type.equals(AppliedPrice.class)) {
                        return AppliedPrice.valueOf(string);
                    } else if (type.equals(MaType.class)) {
                        return MaType.valueOf(string);
                    } else if (type.equals(File.class)) {
                        return new File(string);
                    } else if (type.equals(Color.class)) {
                        return new Color(Integer.valueOf(string));
                    } else if (type.equals(TickBarSize.class)) {
                        return TickBarSize.valueOf(Integer.valueOf(string));
                    } else if (type.equals(PriceRange.class)) {
                        return PriceRange.valueOf(Integer.valueOf(string));
                    } else if (type.equals(ReversalAmount.class)) {
                        return ReversalAmount.valueOf(Integer.valueOf(string));
                    } else if (IFeedDescriptor.class.isAssignableFrom(type)) {
                        return FeedDescriptor.valueOf(string);
                    } else if (!List.class.isAssignableFrom(type) && !Set.class.isAssignableFrom(type)) {
                        if (TimeUnit.class.isAssignableFrom(type)) {
                            return TimeUnit.valueOf(string);
                        } else {
                            LOGGER.error("Unsupported type : {}", type);
                            return null;
                        }
                    } else {
                        JSONArray array = new JSONArray(string);
                        Object result;
                        if (List.class.isAssignableFrom(type)) {
                            result = new ArrayList();
                        } else {
                            result = new HashSet();
                        }

                        for(int i = 0; i < array.length(); i += 2) {
                            String elementStr = array.getString(i);
                            String elementClassStr = array.getString(i + 1);
                            Class<?> elementClass = stringToType(elementClassStr);
                            Object object = stringToValue(elementStr, elementClass);
                            ((Collection)result).add(object);
                        }

                        return result;
                    }
                }
            } catch (Throwable var9) {
                LOGGER.error("Invalid value : " + string, var9);
                return null;
            }
        } else {
            return null;
        }
    }

    public static Class<?> stringToType(String type) {
        Class[] var1 = REMOTELY_SUPPORTED_TYPES;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            Class<?> remotelySupported = var1[var3];
            if (remotelySupported.getName().equals(type)) {
                return remotelySupported;
            }
        }

        try {
            return Class.forName(type);
        } catch (ClassNotFoundException var5) {
            LOGGER.debug("Unsupported type: " + type, var5);
            return null;
        }
    }

    public static boolean isRemotelySupported(Class<?> fieldType) {
        Class[] var1 = REMOTELY_SUPPORTED_TYPES;
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            Class<?> supported = var1[var3];
            if (supported.equals(fieldType)) {
                return true;
            }
        }

        return fieldType.isEnum();
    }

    public static Set<NewsSource> toTransportSources(Set<com.dukascopy.api.INewsFilter.NewsSource> sources) {
        Set<NewsSource> transportSources = new LinkedHashSet();
        Iterator var2 = sources.iterator();

        while(var2.hasNext()) {
            com.dukascopy.api.INewsFilter.NewsSource source = (com.dukascopy.api.INewsFilter.NewsSource)var2.next();
            NewsSource transportSource = toTransportSource(source);
            transportSources.add(transportSource);
        }

        return transportSources;
    }

    public static NewsSource toTransportSource(com.dukascopy.api.INewsFilter.NewsSource source) {
        NewsSource transportSource = NewsSource.valueOf(source.name());
        return transportSource;
    }

    static {
        REMOTELY_SUPPORTED_TYPES = new Class[]{String.class, Integer.TYPE, Integer.class, Long.TYPE, Long.class, Double.TYPE, Double.class, Boolean.TYPE, Boolean.class, Date.class, java.sql.Date.class, Calendar.class, File.class, Color.class, List.class, Set.class, TimeUnit.class, Period.class, Instrument.class, OfferSide.class, Filter.class, Unit.class, DataType.class, AppliedPrice.class, MaType.class, TickBarSize.class, PriceRange.class, ReversalAmount.class, IFeedDescriptor.class};
    }
}
