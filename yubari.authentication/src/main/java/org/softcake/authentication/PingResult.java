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

public class PingResult {


	private boolean pingSuccess;

	private String serverName;

	private String errorMessage;

	private String message;

	private long time;


	public PingResult(boolean pingSuccess, String serverName, long time) {

		this.pingSuccess = pingSuccess;
		this.serverName = serverName;
		this.time = time;
	}


	public PingResult(boolean pingSuccess, String serverName, String errorMessage) {

		this.pingSuccess = pingSuccess;
		this.serverName = serverName;
		this.errorMessage = errorMessage;
	}


	public boolean isPingSuccess() {

		return this.pingSuccess;
	}


	public void setPingSuccess(boolean pingSuccess) {

		this.pingSuccess = pingSuccess;
	}


	public String getServerName() {

		return this.serverName;
	}


	public void setServerName(String serverName) {

		this.serverName = serverName;
	}


	public String getErrorMessage() {

		return this.errorMessage;
	}


	public void setErrorMessage(String errorMessage) {

		this.errorMessage = errorMessage;
	}


	public long getTime() {

		return this.time;
	}


	public void setTime(long time) {

		this.time = time;
	}


	public String getMessage() {

		return this.message;
	}


	public void setMessage(String message) {

		this.message = message;
	}
}
