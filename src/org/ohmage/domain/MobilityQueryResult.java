/*******************************************************************************
 * Copyright 2011 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.domain;

/**
 * @author selsky
 */
public class MobilityQueryResult {
	private Object _value;
	private String _timestamp;
	private String _timezone;
	private String _utcTimestamp;
	private String _locationStatus;
	private String _location;
	
	public Object getValue() {
		return _value;
	}
	public void setValue(Object value) {
		_value = value;
	}
	public String getTimestamp() {
		return _timestamp;
	}
	public void setTimestamp(String timestamp) {
		_timestamp = timestamp;
	}
	public String getTimezone() {
		return _timezone;
	}
	public void setTimezone(String timezone) {
		_timezone = timezone;
	}
	public String getUtcTimestamp() {
		return _utcTimestamp;
	}
	public void setUtcTimestamp(String utcTimestamp) {
		_utcTimestamp = utcTimestamp;
	}
	public String getLocationStatus() {
		return _locationStatus;
	}
	public void setLocationStatus(String locationStatus) {
		_locationStatus = locationStatus;
	}
	public String getLocation() {
		return _location;
	}
	public void setLocation(String location) {
		_location = location;
	}
}