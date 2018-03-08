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

package org.softcake.authentication.concurrent;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;


public class ReadWriteLockUtil {


	private final Lock readLock;

	private final Lock writeLock;


	/**
	 * Simplifying ReadWriteLock with Java 8 and lambdas
	 */
	public ReadWriteLockUtil() {

		this(new ReentrantReadWriteLock());
	}


	/**
	 * @param readWriteLock to inject
	 */
	public ReadWriteLockUtil(final ReadWriteLock readWriteLock) {

		this.readLock = readWriteLock.readLock();
		this.writeLock = readWriteLock.writeLock();
	}


	/**
	 * @param supplier of results in a lock used for reading.
	 * @param <T>      the type of results supplied by this supplier
	 *
	 * @return a result
	 */
	public <T> T read(final Supplier<T> supplier) {

		this.readLock.lock();

		try {

			return supplier.get();

		} finally {

			this.readLock.unlock();

		}
	}


	/**
	 * @param runnable to run in lock used for reading.
	 */
	public void read(final Runnable runnable) {

		this.readLock.lock();

		try {

			runnable.run();

		} finally {

			this.readLock.unlock();

		}
	}


	/**
	 * @param supplier of results in a lock used for writing.
	 * @param <T>      the type of results supplied by this supplier
	 *
	 * @return a result
	 */
	public <T> T write(final Supplier<T> supplier) {

		this.writeLock.lock();

		try {

			return supplier.get();

		} finally {

			this.writeLock.unlock();

		}
	}


	/**
	 * @param runnable to run in a lock used for writing.
	 */
	public void write(final Runnable runnable) {

		this.writeLock.lock();

		try {

			runnable.run();

		} finally {

			this.writeLock.unlock();

		}
	}
}



