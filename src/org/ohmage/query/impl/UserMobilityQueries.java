/*******************************************************************************
 * Copyright 2012 The Regents of the University of California
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
package org.ohmage.query.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import javax.sql.DataSource;

import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.domain.Location;
import org.ohmage.domain.MobilityPoint;
import org.ohmage.domain.MobilityPoint.ClassifierData;
import org.ohmage.domain.MobilityPoint.LocationStatus;
import org.ohmage.domain.MobilityPoint.Mode;
import org.ohmage.domain.MobilityPoint.PrivacyState;
import org.ohmage.domain.MobilityPoint.SubType;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.DomainException;
import org.ohmage.query.IUserMobilityQueries;
import org.ohmage.util.StringUtils;
import org.ohmage.util.TimeUtils;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import edu.ucla.cens.mobilityclassifier.MobilityClassifier;

/**
 * This class contains all of the functionality for creating, reading, 
 * updating, and deleting user-Mobility relationships. While it may read 
 * information pertaining to other entities, the information it takes and  
 * provides should pertain to user-Mobility relationships only.
 * 
 * @author John Jenkins
 */
public final class UserMobilityQueries extends AbstractUploadQuery implements IUserMobilityQueries {
	private static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
	
	// Retrieves the ID for all of the Mobility points that belong to a user.
	private static final String SQL_GET_IDS_FOR_USER = 
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id";
	
	// Retrieves the ID for all of the Mobility points that belong to a user 
	// and were uploaded by a client.
	private static final String SQL_GET_IDS_FOR_CLIENT =
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.client = ?";
	
	// Retrieves the ID for all of the Mobility points that belong to a user 
	// and were created on or after a specified date.
	private static final String SQL_GET_IDS_CREATED_AFTER_DATE = 
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.epoch_millis >= ?";
	
	// Retrieves the ID for all of the Mobility points that belong to a user 
	// and were created on or before a specified date.
	private static final String SQL_GET_IDS_CREATED_BEFORE_DATE =
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.epoch_millis <= ?";
	
	private static final String SQL_GET_IDS_CREATE_BETWEEN_DATES =
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.epoch_millis >= ? " +
		"AND m.epoch_millis <= ?";
	
	// Retrieves the ID for all of the Mobility points that belong to a user 
	// and were uploaded on or after a specified date.
	private static final String SQL_GET_IDS_UPLOADED_AFTER_DATE = 
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.upload_timestamp >= ?";
	
	// Retrieves the ID for all of the Mobility points that belong to a user 
	// and were uploaded on or before a specified date.
	private static final String SQL_GET_IDS_UPLOADED_BEFORE_DATE =
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.upload_timestamp <= ?";
	
	// Retrieves the UD for all of the Mobility points that belong to a user
	// and have a given privacy state.
	private static final String SQL_GET_IDS_WITH_PRIVACY_STATE =
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.privacy_state = ?";
	
	// Retrieves the ID for all of the Mobility points that belong to a user
	// and have a given location status.
	private static final String SQL_GET_IDS_WITH_LOCATION_STATUS = 
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.location_status = ?";
	
	// Retrieves the ID for all of the Mobility points that belong to a user
	// and have a given mode.
	private static final String SQL_GET_IDS_WITH_MODE = 
		"SELECT m.uuid " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND m.mode = ?";

	// Retrieves all of the information pertaining to all Mobility data points 
	// from a collection of given database IDs.
	private static final String SQL_GET_MOBILITY_DATA_FROM_IDS =
		"SELECT m.uuid, u.username, m.client, " +
			"m.epoch_millis, m.upload_timestamp, " +
			"m.phone_timezone, m.location_status, m.location, " +
			"m.mode, mps.privacy_state, " +
			"me.sensor_data, me.features, me.classifier_version " +
		"FROM user u, mobility_privacy_state mps, " +
			"mobility m LEFT JOIN mobility_extended me " +
			"ON m.id = me.mobility_id " +
		"WHERE u.id = m.user_id " +
		"AND mps.id = m.privacy_state_id " +
		"AND m.uuid IN ";
	
	// Retrieves all of the columns necessary to construct a Mobility point
	// and requires only a username.
	private static final String SQL_GET_MOBILITY_DATA =
		"SELECT m.uuid, u.username, m.client, " +
			"m.epoch_millis, m.upload_timestamp, " +
			"m.phone_timezone, m.location_status, m.location, " +
			"m.mode, mps.privacy_state, " +
			"me.sensor_data, me.features, me.classifier_version " +
		"FROM user u, mobility_privacy_state mps, " +
			"mobility m LEFT JOIN mobility_extended me " +
			"ON m.id = me.mobility_id " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND mps.id = m.privacy_state_id";
	
	// Adds a WHERE clause limiting the results to only those on or after a 
	// date represented by the number of milliseconds since the epoch.
	private static final String SQL_WHERE_ON_OR_AFTER_DATE =
		" AND m.epoch_millis >= ?";
	
	// Adds a WHERE clause limiting the results to only those on or before a 
	// date represented by the number of milliseconds since the epoch.
	private static final String SQL_WHERE_ON_OR_BEFORE_DATE =
		" AND m.epoch_millis <= ?";
	
	// Adds a WHERE clause limiting the results to only those with the given
	// privacy state.
	private static final String SQL_WHERE_PRIVACY_STATE =
		" AND m.privacy_state = ?";
	
	// Adds a WHERE clause limiting the results to only those with the given 
	// location status.
	private static final String SQL_WHERE_LOCATION_STATUS =
		" AND m.location_status = ?";
	
	// Adds a WHERE clause limiting the results to only those with the given
	// mode.
	private static final String SQL_WHERE_MODE =
		" AND m.mode = ?";
	
	// Adds an ordering to the results based on their date.
	private static final String SQL_ORDER_BY_DATE =
		" ORDER BY epoch_millis";
	
	// Retrieves the epoch millisecond values for all of the mobility points 
	// for a user within the date ranges.
	private static final String SQL_GET_MIN_MAX_MILLIS_FOR_USER_WITHIN_RANGE_GROUPED_BY_TIME_AND_TIMEZONE =
		"SELECT MIN(m.epoch_millis) as min, MAX(m.epoch_millis) as max, m.phone_timezone " +
		"FROM user u, mobility m " +
		"WHERE u.username = ? " +
		"AND u.id = m.user_id " +
		"AND epoch_millis >= ? " +
		"AND epoch_millis <= ? " +
		"GROUP BY (m.epoch_millis DIV " + MILLIS_PER_DAY + "), m.phone_timezone";

	// Inserts a mode-only entry into the database.
	private static final String SQL_INSERT =
		"INSERT INTO mobility(uuid, user_id, client, epoch_millis, phone_timezone, location_status, location, mode, upload_timestamp, privacy_state_id) " +
		"VALUES (" +
			"?," +
			"(" +		// user_id
				"SELECT id " +
				"FROM user " +
				"WHERE username = ?" +
			"), " +
			"?, " +		// client
			"?, " +		// epoch_millis
			"?, " +		// phone_timezone
			"?, " +		// location_status
			"?, " +		// location
			"?, " +		// mode
			"now(), " +	// upload_timestamp
			"(" +		// privacy_state_id
				"SELECT id " +
				"FROM mobility_privacy_state " +
				"WHERE privacy_state = ?" +
			")" +
		")";
	
	// Inserts an extended entry into the database.
	private static final String SQL_INSERT_EXTENDED =
		"INSERT INTO mobility_extended(mobility_id, sensor_data, features, classifier_version) " +
		"VALUES (" +
			"?, " +		// mobility_id
			"?, " +		// sensor_data
			"?, " +		// features
			"?" +		// classifier_version
		")";
	
	/**
	 * Creates this object.
	 * 
	 * @param dataSource The DataSource to use when accessing the database.
	 */
	private UserMobilityQueries(DataSource dataSource) {
		super(dataSource);
	}
	
	/**
	 * Creates a new Mobility point.
	 * 
	 * @param username The username of the user to which this point belongs.
	 * 
	 * @param client The client value given on upload.
	 * 
	 * @param mobilityPoint The Mobility point to be created.
	 * 
	 * @throws DataAccessException Thrown if there is an error.
	 */
	public void createMobilityPoint(
			final String username, 
			final String client,
			final MobilityPoint mobilityPoint) 
			throws DataAccessException {
		
		// Create the transaction.
		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setName("Creating a Mobility data point.");
		
		try {
			// Begin the transaction.
			PlatformTransactionManager transactionManager = new DataSourceTransactionManager(getDataSource());
			TransactionStatus status = transactionManager.getTransaction(def);
			
			JSONObject location = null;
			try {
				Location tLocation = mobilityPoint.getLocation();
				if(tLocation != null) {
					location = tLocation.toJson(false);	
				}
			}
			catch(JSONException e) {
				throw new DataAccessException(e);
			}

			try {
				KeyHolder mobilityPointDatabaseKeyHolder = new GeneratedKeyHolder();
				getJdbcTemplate().update(
					new PreparedStatementCreator() {
						public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
							PreparedStatement ps = connection.prepareStatement(SQL_INSERT, new String[] {"id"});
							
							ps.setString(1, mobilityPoint.getId().toString());
							ps.setString(2, username);
							ps.setString(3, client);
							
							ps.setLong(4, mobilityPoint.getTime());
							ps.setString(5, mobilityPoint.getTimezone().getID());
							
							ps.setString(6, mobilityPoint.getLocationStatus().toString().toLowerCase());
							try {
								Location location = mobilityPoint.getLocation();
								ps.setString(7, ((location == null) ? null : location.toJson(false).toString()));
							} 
							catch(JSONException e) {
								throw new SQLException(
										"Could not create a JSONObject for the location.",
										e);
							}
							
							ps.setString(8, mobilityPoint.getMode().toString().toLowerCase());
							
							ps.setString(9, mobilityPoint.getPrivacyState().toString());
							
							return ps;
						}
					},
					mobilityPointDatabaseKeyHolder
				);
				
				// If it's an extended entry, add the sensor data.
				if(SubType.SENSOR_DATA.equals(mobilityPoint.getSubType())) {
					JSONObject sensorData;
					try {
						sensorData = mobilityPoint.getSensorData().toJson(false);
					}
					catch(JSONException e) {
						throw new DataAccessException(e);
					}
					
					JSONObject classifierData;
					try {
						ClassifierData tClassifierData = 
								mobilityPoint.getClassifierData();
						
						if(tClassifierData == null) {
							classifierData = null;
						}
						else {
							classifierData = tClassifierData.toJson();
						}
					}
					catch(JSONException e) {
						throw new DataAccessException(e);
					}
					
					try {
						getJdbcTemplate().update(
								SQL_INSERT_EXTENDED,
								mobilityPointDatabaseKeyHolder.getKey().longValue(),
								sensorData.toString(),
								(classifierData == null) ? (new JSONObject()).toString() : classifierData.toString(),
								MobilityClassifier.getVersion());
					}
					catch(org.springframework.dao.DataAccessException e) {
						throw new DataAccessException(
								"Error executing SQL '" + 
										SQL_INSERT_EXTENDED + 
									"' with parameters: " +
										mobilityPointDatabaseKeyHolder.getKey().longValue() + ", " +
										sensorData.toString() + ", " +
										((classifierData == null) ? (new JSONObject()).toString() : classifierData.toString()) + ", " +
										MobilityClassifier.getVersion(),
								e);
					}
				}
			}
			// If this is a duplicate upload, we will ignore it by rolling back
			// to where we were before we started and return.
			catch(org.springframework.dao.DataIntegrityViolationException e) {
				// FIXME: Now that we use UUIDs, the client should not be 
				// submitting duplicates. We probably want to, at the very 
				// least make a warning message and at most fail the request.
				if(! isDuplicate(e)) {
					throw new DataAccessException(
							"Error executing SQL '" + SQL_INSERT + "' with parameters: " +
								mobilityPoint.getId().toString() + ", " +
								username + ", " +
								client + ", " +
								mobilityPoint.getTime() + ", " +
								mobilityPoint.getTimezone().getID() + ", " +
								mobilityPoint.getLocationStatus().toString().toLowerCase() + ", " +
								((location == null) ? "null" : location.toString()) + ", " +
								mobilityPoint.getMode().toString().toLowerCase() + ", " +
								mobilityPoint.getPrivacyState(),
							e);
				}
			}
			catch(org.springframework.dao.DataAccessException e) {
				throw new DataAccessException(
						"Error executing SQL '" + SQL_INSERT + "' with parameters: " +
							mobilityPoint.getId().toString() + ", " +
							username + ", " +
							client + ", " +
							mobilityPoint.getTime() + ", " +
							mobilityPoint.getTimezone().getID() + ", " +
							mobilityPoint.getLocationStatus().toString().toLowerCase() + ", " +
							((location == null) ? "null" : location.toString()) + ", " +
							mobilityPoint.getMode().toString().toLowerCase() + ", " +
							mobilityPoint.getPrivacyState(),
						e);
			}
			
			// Commit the transaction.
			try {
				transactionManager.commit(status);
			}
			catch(TransactionException e) {
				transactionManager.rollback(status);
				throw new DataAccessException("Error while committing the transaction.", e);
			}
		}
		catch(TransactionException e) {
			throw new DataAccessException("Error while attempting to rollback the transaction.", e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsForUser(java.lang.String)
	 */
	public List<String> getIdsForUser(String username) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_FOR_USER,
					new Object[] { username },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_FOR_USER + 
						"' with parameter: " + 
							username,
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsForClient(java.lang.String, java.lang.String)
	 */
	public List<String> getIdsForClient(String username, String client) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_FOR_CLIENT,
					new Object[] { username, client },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_FOR_CLIENT + 
						"' with parameters: " + 
							username + ", " +
							client,
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsCreatedAfterDate(java.lang.String, java.util.Date)
	 */
	public List<String> getIdsCreatedAfterDate(String username, Date startDate) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_CREATED_AFTER_DATE,
					new Object[] { username, startDate.getTime() },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_CREATED_AFTER_DATE + 
						"' with parameters: " + 
							username + ", " +
							startDate.getTime(),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsCreatedBeforeDate(java.lang.String, java.util.Date)
	 */
	public List<String> getIdsCreatedBeforeDate(String username, Date endDate) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_CREATED_BEFORE_DATE,
					new Object[] { username, endDate.getTime() },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_CREATED_BEFORE_DATE + 
						"' with parameters: " + 
							username + ", " +
							endDate.getTime(),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsCreatedBetweenDates(java.lang.String, java.util.Date, java.util.Date)
	 */
	public List<String> getIdsCreatedBetweenDates(String username, Date startDate, Date endDate) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_CREATE_BETWEEN_DATES,
					new Object[] { username, startDate.getTime(), endDate.getTime() },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_CREATED_BEFORE_DATE + 
						"' with parameters: " + 
							username + ", " +
							startDate.getTime() + ", " +
							endDate.getTime(),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsUploadedAfterDate(java.lang.String, java.util.Date)
	 */
	public List<String> getIdsUploadedAfterDate(String username, Date startDate) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_UPLOADED_AFTER_DATE,
					new Object[] { username, TimeUtils.getIso8601DateString(startDate) },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_UPLOADED_AFTER_DATE + 
						"' with parameters: " + 
							username + ", " +
							TimeUtils.getIso8601DateString(startDate),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsUploadedBeforeDate(java.lang.String, java.util.Date)
	 */
	public List<String> getIdsUploadedBeforeDate(String username, Date endDate) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_UPLOADED_BEFORE_DATE,
					new Object[] { username, TimeUtils.getIso8601DateString(endDate) },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_UPLOADED_BEFORE_DATE + 
						"' with parameters: " + 
							username + ", " +
							TimeUtils.getIso8601DateString(endDate),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsWithPrivacyState(java.lang.String, org.ohmage.domain.MobilityPoint.PrivacyState)
	 */
	public List<String> getIdsWithPrivacyState(String username, MobilityPoint.PrivacyState privacyState) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_WITH_PRIVACY_STATE,
					new Object[] { username, privacyState.toString() },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_WITH_PRIVACY_STATE + 
						"' with parameters: " + 
							username + ", " +
							privacyState,
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsWithLocationStatus(java.lang.String, org.ohmage.domain.MobilityPoint.LocationStatus)
	 */
	public List<String> getIdsWithLocationStatus(String username, LocationStatus locationStatus) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_WITH_LOCATION_STATUS,
					new Object[] { username, locationStatus.toString().toLowerCase() },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_WITH_LOCATION_STATUS + 
						"' with parameters: " + 
							username + ", " +
							locationStatus.toString().toLowerCase(),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getIdsWithMode(java.lang.String, org.ohmage.domain.MobilityPoint.Mode)
	 */
	public List<String> getIdsWithMode(String username, Mode mode) throws DataAccessException {
		try {
			return getJdbcTemplate().query(
					SQL_GET_IDS_WITH_MODE,
					new Object[] { username, mode.toString().toLowerCase() },
					new SingleColumnRowMapper<String>());
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_IDS_WITH_MODE + 
						"' with parameters: " + 
							username + ", " +
							mode.toString().toLowerCase(),
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getMobilityInformationFromIds(java.util.Collection)
	 */
	public List<MobilityPoint> getMobilityInformationFromIds(
			Collection<String> ids) 
			throws DataAccessException {

		String sql =
				SQL_GET_MOBILITY_DATA_FROM_IDS + 
				StringUtils.generateStatementPList(ids.size()) + 
				" ORDER BY m.epoch_millis";
		
		try {
			return getJdbcTemplate().query(
					sql,
					ids.toArray(),
					new RowMapper<MobilityPoint>() {
						@Override
						public MobilityPoint mapRow(ResultSet rs, int rowNum) throws SQLException {
							try {
								JSONObject location = null;
								String locationString = rs.getString("location");
								if(locationString != null) {
									location = new JSONObject(locationString);
								}
								
								JSONObject sensorData = null;
								String sensorDataString = rs.getString("sensor_data");
								if(sensorDataString != null) {
									sensorData = new JSONObject(sensorDataString);
								}
								
								JSONObject features = null;
								String featuresString = rs.getString("features");
								if(featuresString != null) {
									features = new JSONObject(featuresString);
								}
								
								return new MobilityPoint(
										UUID.fromString(rs.getString("uuid")),
										rs.getLong("epoch_millis"),
										TimeZone.getTimeZone(rs.getString("phone_timezone")),
										LocationStatus.valueOf(rs.getString("location_status").toUpperCase()),
										location,
										Mode.valueOf(rs.getString("mode").toUpperCase()),
										MobilityPoint.PrivacyState.getValue(rs.getString("privacy_state")),
										sensorData,
										features,
										rs.getString("classifier_version"));
							}
							catch(JSONException e) {
								throw new SQLException("Error building a JSONObject.", e);
							}
							catch(DomainException e) {
								throw new SQLException("Error building the MobilityInformation object. This suggests malformed data in the database.", e);
							}
							catch(IllegalArgumentException e) {
								throw new SQLException("Error building the MobilityInformation object. This suggests malformed data in the database.", e);
							}
						}
					}
				);
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							sql + 
						"' with parameter: " + 
							ids,
					e);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getMobilityInformation(java.lang.String, java.util.Date, java.util.Date, org.ohmage.domain.MobilityPoint.PrivacyState, org.ohmage.domain.MobilityPoint.LocationStatus, org.ohmage.domain.MobilityPoint.Mode)
	 */
	@Override
	public List<MobilityPoint> getMobilityInformation(
			final String username,
			final Date startDate, 
			final Date endDate, 
			final PrivacyState privacyState,
			final LocationStatus locationStatus, 
			final Mode mode)
			throws DataAccessException {

		StringBuilder sqlBuilder = new StringBuilder(SQL_GET_MOBILITY_DATA);
		List<Object> parameters = new LinkedList<Object>();
		parameters.add(username);
		
		if(startDate != null) {
			sqlBuilder.append(SQL_WHERE_ON_OR_AFTER_DATE);
			parameters.add(startDate.getTime());
		}
		if(endDate != null) {
			sqlBuilder.append(SQL_WHERE_ON_OR_BEFORE_DATE);
			parameters.add(endDate.getTime());
		}
		if(privacyState != null) {
			sqlBuilder.append(SQL_WHERE_PRIVACY_STATE);
			parameters.add(privacyState.toString());
		}
		if(locationStatus != null) {
			sqlBuilder.append(SQL_WHERE_LOCATION_STATUS);
			parameters.add(locationStatus.toString());
		}
		if(mode != null) {
			sqlBuilder.append(SQL_WHERE_MODE);
			parameters.add(mode.toString().toLowerCase());
		}
		
		sqlBuilder.append(SQL_ORDER_BY_DATE);
		
		try {
			return getJdbcTemplate().query(
					sqlBuilder.toString(),
					parameters.toArray(),
					new RowMapper<MobilityPoint>() {
						@Override
						public MobilityPoint mapRow(ResultSet rs, int rowNum) throws SQLException {
							try {
								JSONObject location = null;
								String locationString = rs.getString("location");
								if(locationString != null) {
									location = new JSONObject(locationString);
								}
								
								JSONObject sensorData = null;
								String sensorDataString = rs.getString("sensor_data");
								if(sensorDataString != null) {
									sensorData = new JSONObject(sensorDataString);
								}
								
								JSONObject features = null;
								String featuresString = rs.getString("features");
								if(featuresString != null) {
									features = new JSONObject(featuresString);
								}
								
								return new MobilityPoint(
										UUID.fromString(rs.getString("uuid")),
										rs.getLong("epoch_millis"),
										TimeZone.getTimeZone(rs.getString("phone_timezone")),
										LocationStatus.valueOf(rs.getString("location_status").toUpperCase()),
										location,
										Mode.valueOf(rs.getString("mode").toUpperCase()),
										MobilityPoint.PrivacyState.getValue(rs.getString("privacy_state")),
										sensorData,
										features,
										rs.getString("classifier_version"));
							}
							catch(JSONException e) {
								throw new SQLException("Error building a JSONObject.", e);
							}
							catch(DomainException e) {
								throw new SQLException("Error building the MobilityInformation object. This suggests malformed data in the database.", e);
							}
							catch(IllegalArgumentException e) {
								throw new SQLException("Error building the MobilityInformation object. This suggests malformed data in the database.", e);
							}
						}
					}
				);
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							sqlBuilder.toString() + 
						"' with parameters: " + 
							parameters,
					e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.query.IUserMobilityQueries#getDates(java.util.Date, java.util.Date, java.lang.String)
	 */
	public Set<Date> getDates(
			final Date startDate,
			final Date endDate,
			final String username)
			throws DataAccessException {
		

		List<Object> parameters = new ArrayList<Object>(3);
		parameters.add(username);
		parameters.add(startDate.getTime());
		parameters.add(endDate.getTime());
		
		try {
			return getJdbcTemplate().query(
					SQL_GET_MIN_MAX_MILLIS_FOR_USER_WITHIN_RANGE_GROUPED_BY_TIME_AND_TIMEZONE, 
					parameters.toArray(),
					new ResultSetExtractor<Set<Date>>() {
						/**
						 * Gathers the applicable dates based on their time 
						 * zone.
						 */
						@Override
						public Set<Date> extractData(ResultSet rs)
								throws SQLException,
								org.springframework.dao.DataAccessException {
							
							Set<Date> result = new HashSet<Date>();
							
							while(rs.next()) {
								Calendar userCalendar = 
										Calendar.getInstance(
												TimeZone.getTimeZone(
														rs.getString(
																"phone_timezone")));
								Calendar serverCalendar = 
										Calendar.getInstance();
								
								serverCalendar.setTimeInMillis(0);
								userCalendar.setTimeInMillis(rs.getLong("min"));
								serverCalendar.set(
										Calendar.YEAR, 
										userCalendar.get(Calendar.YEAR));
								serverCalendar.set(
										Calendar.MONTH, 
										userCalendar.get(Calendar.MONTH));
								serverCalendar.set(
										Calendar.DAY_OF_MONTH, 
										userCalendar.get(Calendar.DAY_OF_MONTH));
								result.add(serverCalendar.getTime());

								serverCalendar.setTimeInMillis(0);
								userCalendar.setTimeInMillis(rs.getLong("max"));
								serverCalendar.set(
										Calendar.YEAR, 
										userCalendar.get(Calendar.YEAR));
								serverCalendar.set(
										Calendar.MONTH, 
										userCalendar.get(Calendar.MONTH));
								serverCalendar.set(
										Calendar.DAY_OF_MONTH, 
										userCalendar.get(Calendar.DAY_OF_MONTH));
								result.add(serverCalendar.getTime());
							}
							
							return result;
						}
					}
				);
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error executing SQL '" +
							SQL_GET_MIN_MAX_MILLIS_FOR_USER_WITHIN_RANGE_GROUPED_BY_TIME_AND_TIMEZONE + 
						"' with parameters: " + 
							parameters,
					e);
		}
	}
	
	/**
	 * Retrieves the timestamp of last Mobility upload from a user.
	 * 
	 * @param username The user's username.
	 * 
	 * @return Returns a Timestamp representing the date and time that the last
	 * 		   Mobility upload from a user took place. If no Mobility data was
	 * 		   ever uploaded, null is returned.
	 */
	public Date getLastUploadForUser(String username) throws DataAccessException {
		try {
			List<Long> timestamps = getJdbcTemplate().query(
					SQL_GET_MOBILITY_DATA, 
					new Object[] { username }, 
					new RowMapper<Long>() {
						@Override
						public Long mapRow(ResultSet rs, int rowNum) throws SQLException {
							return rs.getLong("epoch_millis");
						}
					}
				);
			
			if(timestamps.size() > 0) {
				Collections.sort(timestamps);
				return new Date(timestamps.get(timestamps.size() - 1));
			}
			else {
				return null;
			}
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error while executing '" + 
							SQL_GET_MOBILITY_DATA + 
						"' with parameters: " + 
							username, 
					e);
		}
	}
	
	/**
	 * Returns the percentage of non-null location values that were uploaded in
	 * the last 'hours'.
	 * 
	 * @param username The user's username.
	 * 
	 * @param hours The number of hours before now to find applicable uploads.
	 * 
	 * @return The percentage of non-null Mobility uploads or null if there
	 * 		   were none. 
	 */
	public Double getPercentageOfNonNullLocations(String username, int hours) 
		throws DataAccessException {
		
		try {
			// Get a time stamp from 'hours' ago.
			Calendar dayAgo = Calendar.getInstance();
			dayAgo.add(Calendar.HOUR_OF_DAY, -hours);
			final long dayAgoMillis = dayAgo.getTimeInMillis();
			
			final List<String> nonNullLocations = new LinkedList<String>();
			final List<String> allLocations = new LinkedList<String>();
			
			getJdbcTemplate().query(
					SQL_GET_MOBILITY_DATA, 
					new Object[] { username }, 
					new RowMapper<Object>() {
						@Override
						public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
							long currPointMillis = rs.getLong("epoch_millis");
							
							if(currPointMillis >= dayAgoMillis) {
								String location = rs.getString("location");
								if(location != null) {
									nonNullLocations.add(location);
								}
								allLocations.add(location);
							}
							
							return null;
						}
					}
				);
			
			if(allLocations.size() == 0) {
				return null;
			}
			else {
				return new Double(nonNullLocations.size()) / new Double(allLocations.size());
			}
		}
		catch(org.springframework.dao.DataAccessException e) {
			throw new DataAccessException(
					"Error while executing '" + 
							SQL_GET_MOBILITY_DATA + 
						"' with parameters: " + 
							username, 
					e);
		}
	}
}
