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

import org.softcake.cherry.core.base.PreCheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;


/**
 * The {@code AuthorizationUrlPool} saves the authorization URL´s
 * for use in {@link AuthorizationClient}.
 *
 * @author The Softcake Authors.
 */
public class AuthorizationUrlPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationUrlPool.class);
    private LinkedList<URL> authServerList = new LinkedList();

    /**
     * Returns the number of {@code URL's} in this pool.
     *
     * @return the number of URL´s in this pool
     */
    public int size() {

        return this.authServerList.size();
    }


    /**
     * Appends the specified link to the end of this pool.
     *
     * @param link URL as String to be appended to this pool
     * @deprecated refactoring advice
     */
    @Deprecated(since = "1.0.0")
    public void add(final String link) {

        PreCheck.parameterNotNullOrEmpty(link, "link");
        try {
            this.add(new URL(link));
        } catch (final MalformedURLException e) {
            LOGGER.error("Error occurred...", e);
        }
    }

    /**
     * Appends the specified {@code URL} to the end of this pool.
     * If this pool contains this {@code URL} already, the {@code URL} will be
     * moved to the end of this pool.
     *
     * @param url {@link URL} to be appended to this pool
     */
    public void add(final URL url) {

        PreCheck.parameterNotNull(url, "url");
        if (this.authServerList.contains(url)) {
            this.authServerList.remove(url);
        }
        this.authServerList.addLast(url);
    }

    /**
     * Appends all of the elements in the specified collection to the end of
     * this pool, in the order that they are returned by the specified
     * collection's iterator.  The behavior of this operation is undefined if
     * the specified collection is modified while the operation is in
     * progress.  (Note that this will occur if the specified collection is
     * this list, and it's nonempty.)
     *
     * @param urls collection containing {@code URL's} to be added to this pool
     */
    public void addAll(final Collection<URL> urls) {

        PreCheck.parameterNotNullOrEmpty(urls, "urls");
        urls.forEach(this::add);
    }


    /**
     * Returns the first {@code URL}  in this pool.
     *
     * @return the {@link URL} in this pool
     */
    public URL get() {

        return this.authServerList.getFirst();
    }

    /**
     * Moves an {@code URL} from the first position to the last of this pool. In other
     * words, marks this {@code URL}  as a bad one.
     */
    public void markLastUsedAsBad() {

        this.authServerList.addLast(this.authServerList.pop());
    }

    /**
     * Removes all of the {@code URL's} from this pool.
     * The pool will be empty after this call returns.
     */
    public void clear() {

        this.authServerList.clear();
    }
}
