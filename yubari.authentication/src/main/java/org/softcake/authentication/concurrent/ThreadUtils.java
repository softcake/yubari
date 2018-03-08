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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


public final class ThreadUtils {


	private static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtils.class);

	private static final String THREAD_SUFFIX = "-Thread";


	private ThreadUtils() {

	}


	public static void safeSleep(final long duration, final TimeUnit timeUnit) {

		safeSleep(timeUnit.toMillis(duration));
	}


	public static void safeSleep(final long durationInMilliSecs) {

		try {

			Thread.sleep(durationInMilliSecs);

		} catch (final InterruptedException e) {

			LOGGER.error("Error occurred...", e);
			Thread.currentThread()
				  .interrupt();

		}
	}


	public static void printStarted(Thread thread) {

		LOGGER.debug(thread.getName() + THREAD_SUFFIX + " successfully started");
	}


	public static void printShutDown(Thread thread) {

		LOGGER.debug(thread.getName() + THREAD_SUFFIX + " shutdown now...");
	}


	public static void printStopped(Thread thread) {

		LOGGER.debug(thread.getName() + THREAD_SUFFIX + " successfully stopped");
	}

}
