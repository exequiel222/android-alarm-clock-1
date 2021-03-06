package pl.sointeractive.isaaclock.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data store class for Notifications.
 * @author Mateusz Renes
 *
 */
public class Notification {

	JSONObject data;
	private String title, message;
	
	public Notification(JSONObject data, String title, String message){
		this.data = data;
		this.title = title;
		this.message = message;
	}
	
	public Notification(JSONObject json) throws JSONException{
		this.data = json.getJSONObject("data");
		this.title = data.getString("title");
		this.message = data.getString("message");
	}

	public JSONObject getData() {
		return data;
	}

	public void setData(JSONObject data) {
		this.data = data;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	
}
