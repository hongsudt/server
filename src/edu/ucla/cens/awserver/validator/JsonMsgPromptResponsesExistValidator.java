package edu.ucla.cens.awserver.validator;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.ucla.cens.awserver.datatransfer.AwRequest;
import edu.ucla.cens.awserver.util.JsonUtils;

/**
 * Validator for the responses element of a prompt message.
 * 
 * @author selsky
 */
public class JsonMsgPromptResponsesExistValidator extends AbstractAnnotatingJsonObjectValidator {
	private String _key = "responses";
		
	public JsonMsgPromptResponsesExistValidator(AwRequestAnnotator awRequestAnnotator) {
		super(awRequestAnnotator);
	}
	
	/**
	 * @return true if the responses array exists and is not empty
	 * @return false otherwise
	 */
	public boolean validate(AwRequest request, JSONObject jsonObject) {
		JSONArray array = JsonUtils.getJsonArrayFromJson(jsonObject, _key);
		
		if(null == array || array.length() == 0) {
			getAnnotator().annotate(request, "responses array from prompt message is null or empty");
			return false;
		}
		
		return true;
	}
}
