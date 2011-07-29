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
package org.ohmage.jee.servlet.validator;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.ohmage.cache.CacheMissException;
import org.ohmage.cache.PreferenceCache;
import org.ohmage.request.DocumentCreationAwRequest;
import org.ohmage.request.InputKeys;
import org.ohmage.util.CookieUtils;
import org.ohmage.util.StringUtils;


/**
 * Validates an incoming HTTP request to create a new document.
 * 
 * @author John Jenkins
 */
public class DocumentCreationValidator extends AbstractHttpServletRequestValidator {
	private static Logger _logger = Logger.getLogger(DocumentCreationValidator.class);
	
	private DiskFileItemFactory _diskFileItemFactory;
	
	/**
	 * Creates this validator with a DiskFileItemFactory which will regulate
	 * when an item should be put on disk and when it should be left in
	 * memory.
	 * 
	 * @param diskFileItemFactory The DiskFileItemFactory that will determine
	 * 							  when an item should be put to disk and when
	 * 							  it should be left in memory.
	 */
	public DocumentCreationValidator(DiskFileItemFactory diskFileItemFactory) {
		if(diskFileItemFactory == null) {
			throw new IllegalArgumentException("The disk file item factory cannot be null.");
		}
		
		_diskFileItemFactory = diskFileItemFactory;
	}

	/**
	 * Checks that the document values are sane.
	 * 
	 * Also, it creates the request object and stores it back in the
	 * 'httpRequest' to be grabbed by the glue.
	 * 
	 * @throws MissingAuthTokenException Thrown if the authentication / session
	 * 									 token is missing or whitespace only. 
	 */
	@Override
	public boolean validate(HttpServletRequest httpRequest) throws MissingAuthTokenException {
		// Get the authentication / session token from the header.
		String token = null;
		List<String> tokens = CookieUtils.getCookieValue(httpRequest.getCookies(), InputKeys.AUTH_TOKEN);
		if(tokens.size() == 0) {
			// This is the temporary fix where we are allowing the token to be
			// either a cookie or a parameter.
			//throw new MissingAuthTokenException("The required authentication / session token is missing.");
		}
		else if(tokens.size() > 1) {
			throw new MissingAuthTokenException("More than one authentication / session token was found in the request.");
		}
		else {
			token = tokens.get(0);
		}
		
		// Create a new file upload handler
		ServletFileUpload upload = new ServletFileUpload(_diskFileItemFactory);
		// TODO: Is this necessary? Should it even be done?
		//upload.setHeaderEncoding("UTF-8");
		
		// Set the maximum allowed size of a document.
		try {
			upload.setFileSizeMax(Long.valueOf(PreferenceCache.instance().lookup(PreferenceCache.KEY_MAXIMUM_DOCUMENT_SIZE)));
		}
		catch(CacheMissException e) {
			_logger.error("Cache miss while trying to get maximum allowed document size.", e);
			return false;
		}
		
		// Parse the request
		List<?> uploadedItems = null;
		try {
		
			uploadedItems = upload.parseRequest(httpRequest);
		
		} catch(FileUploadException fue) { 			
			_logger.error("Caught exception while uploading a document.", fue);
			return false;
		}
		
		// Get the number of items were in the request.
		int numberOfUploadedItems = uploadedItems.size();
		
		// Parse the request for each of the parameters.
		String name = null;
		byte[] document = null;
		String privacyState = null;
		String description = null;
		String campaignUrnRoleList = null;
		String classUrnRoleList = null;
		for(int i = 0; i < numberOfUploadedItems; i++) {
			FileItem fi = (FileItem) uploadedItems.get(i);
			if(fi.isFormField()) {
				String fieldName = fi.getFieldName();
				String fieldValue = StringUtils.urlDecode(fi.getString());
				
				if(InputKeys.DOCUMENT_NAME.equals(fieldName)) {
					if(greaterThanLength(InputKeys.DOCUMENT_NAME, InputKeys.DOCUMENT_NAME, fieldValue, 255)) {
						return false;
					}
					name = fieldValue;
				}
				else if(InputKeys.PRIVACY_STATE.equals(fieldName)) {
					if(greaterThanLength(InputKeys.PRIVACY_STATE, InputKeys.PRIVACY_STATE, fieldValue, 50)) {
						return false;
					}
					privacyState = fieldValue;
				}
				else if(InputKeys.DESCRIPTION.equals(fieldName)) {
					// This requirement has more to do with the database than
					// with anything else.
					if(greaterThanLength(InputKeys.DESCRIPTION, InputKeys.DESCRIPTION, fieldValue, 65535)) {
						return false;
					}
					description = fieldValue;
				}
				else if(InputKeys.DOCUMENT_CAMPAIGN_ROLE_LIST.equals(fieldName)) {
					// This requirement has more to do with the database than
					// with anything else.
					if(greaterThanLength(InputKeys.DOCUMENT_CAMPAIGN_ROLE_LIST, InputKeys.DOCUMENT_CAMPAIGN_ROLE_LIST, fieldValue, 65535)) {
						return false;
					}
					campaignUrnRoleList = fieldValue;
				}
				else if(InputKeys.DOCUMENT_CLASS_ROLE_LIST.equals(fieldName)) {
					// This requirement has more to do with the database than
					// with anything else.
					if(greaterThanLength(InputKeys.DOCUMENT_CLASS_ROLE_LIST, InputKeys.DOCUMENT_CLASS_ROLE_LIST, fieldValue, 65535)) {
						return false;
					}
					classUrnRoleList = fieldValue;
				}
				else if(InputKeys.AUTH_TOKEN.equals(fieldName)) {
					if(greaterThanLength(InputKeys.AUTH_TOKEN, InputKeys.AUTH_TOKEN, fieldValue, 36)) {
						return false;
					}
					token = fieldValue;
				}
			} else {
				if(InputKeys.DOCUMENT.equals(fi.getFieldName())) {					
					document = fi.get(); // Gets the document.
				}
			}
		}
		
		DocumentCreationAwRequest request;
		try {
			request = new DocumentCreationAwRequest(name, document, privacyState, description, campaignUrnRoleList, classUrnRoleList);
		}
		catch(IllegalArgumentException e) {
			_logger.warn("Attempting to create a request with insufficient parameters: " + e.getMessage());
			return false;
		}
		
		if(token == null) {
			throw new MissingAuthTokenException("The required authentication / session token is missing.");
		}
		else {
			request.setUserToken(token);
		}
		
		// This is done so we only need to read the request once as it may be
		// a large amount of data. This does, however, break out model of
		// checking the data first (here) and creating the the request later
		// in a glue object. I put a higher priority on following the model
		// than I do creating this here, but I am following conventions for
		// now.
		httpRequest.setAttribute("awRequest", request);
		
		return true;
	}
}