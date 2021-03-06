package pl.sointeractive.isaaclock.fragments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;

import org.json.JSONException;
import org.json.JSONObject;

import pl.sointeractive.isaaclock.R;
import pl.sointeractive.isaaclock.activities.UserActivity;
import pl.sointeractive.isaaclock.activities.UserActivity.TabManager;
import pl.sointeractive.isaaclock.alarm.AlarmService;
import pl.sointeractive.isaaclock.data.Alarm;
import pl.sointeractive.isaaclock.data.App;
import pl.sointeractive.isaaclock.data.UserData;
import pl.sointeractive.isaacloud.connection.HttpResponse;
import pl.sointeractive.isaacloud.exceptions.IsaaCloudConnectionException;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TimePicker;

import com.actionbarsherlock.app.SherlockListFragment;

/**
 * Fragment class for displaying Alarms. Used in the UserActivity and shown in
 * its corresponding Tab. It extends SherlockListFragment, which is a ABS
 * library version of the Android ListFragment class. For detailed information
 * on how the ListFragment handles its data viewing, please check the class
 * documentation provided by Google.
 * 
 * @author Mateusz Renes
 * 
 */
public class AlarmsFragment extends SherlockListFragment {

	private static final String TAG = "AlarmsFragment";

	UserActivity context;
	AlarmAdapter alarmAdapter;
	ArrayList<Alarm> alarmList;
	UserData userData;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		context = (UserActivity) getSherlockActivity();
		userData = App.loadUserData();
		alarmList = userData.getAlarms();
		alarmAdapter = new AlarmAdapter(context);
		setListAdapter(alarmAdapter);
		return inflater.inflate(R.layout.fragment_basic_listview, container,
				false);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		// If the selected alarm is active, open options. If not, set alarm.
		if (alarmList.get(position).isActive()) {
			openOptionsDialog(position);
		} else {
			setTime(position);
		}
	}

	/**
	 * Deactivate the selected alarm and restart the alarm service.
	 * 
	 * @param dayIndex
	 *            The index of the desired day (0-Monday, 6-Sunday) in the alarm
	 *            list.
	 */
	private void deactivateAlarm(final int dayIndex) {
		alarmList.get(dayIndex).setActive(false);
		App.saveUserData(userData);
		refreshCurrentFragment();
		Log.d(TAG, userData.printAlarms());
		startAlarmService();
	}

	/**
	 * Set time to the selected alarm and restart the alarm service.
	 * 
	 * @param dayIndex
	 *            The index of the desired day (0-Monday, 6-Sunday) in the alarm
	 *            list.
	 */
	private void setTime(final int dayIndex) {
		// setup time picker dialog
		TimePickerDialog.OnTimeSetListener timePickerListener = new TimePickerDialog.OnTimeSetListener() {
			public void onTimeSet(TimePicker view, int selectedHour,
					int selectedMinute) {
				userData.setAlarm(dayIndex, selectedHour, selectedMinute, true);
				alarmList = userData.getAlarms();
				App.saveUserData(userData);
				refreshCurrentFragment();
				Log.d(TAG, userData.printAlarms());
				startAlarmService();
				new PostEventTask().execute();
			}
		};
		// get time preferences
		Calendar c = Calendar.getInstance();
		c.add(Calendar.MINUTE, 1);
		boolean isUsing24HourTime = App.loadUserData().isUsing24HourTime();
		// show time picker dialog
		new TimePickerDialog(context, timePickerListener,
				c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
				isUsing24HourTime).show();
	}

	/**
	 * Refresh view of the current tab.
	 */
	private void refreshCurrentFragment() {
		Log.d(TAG, "refreshCurrentFragment()");
		TabManager tm = context.getTabManager();
		TabHost th = context.getTabHost();
		tm.printTabInfo();
		tm.refreshTab(th.getCurrentTabTag());
	}

	/**
	 * Open options dialog for the selected alarm. Used to deactivate the alarm
	 * or change its time.
	 * 
	 * @param dayIndex
	 */
	private void openOptionsDialog(final int dayIndex) {
		AlertDialog dialog;
		String[] options = {
				getString(R.string.fragment_alarms_dialog_option_change_time),
				getString(R.string.fragment_alarms_dialog_option_deactivate) };
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.fragment_alarms_dialog_title);
		builder.setNeutralButton(R.string.fragment_alarms_dialog_button_cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		builder.setItems(options, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case 0:
					setTime(dayIndex);
					dialog.dismiss();
					break;
				case 1:
					deactivateAlarm(dayIndex);
					dialog.dismiss();
					break;
				}
			}
		});
		dialog = builder.create();
		dialog.show();
	}

	/**
	 * Stop any active AlarmService instances and start a new one.
	 */
	private void startAlarmService() {
		Log.d(TAG, "startAlarmService()");
		Intent intent = new Intent(context.getApplicationContext(),
				AlarmService.class);
		intent.putExtra("SNOOZE_COUNTER", 0);
		context.getApplicationContext().stopService(intent);
		context.getApplicationContext().startService(intent);
	}

	/**
	 * This AsyncTask is used for posting event data to the API after an alarm
	 * is set.
	 * 
	 * @author Mateusz Renes
	 * 
	 */
	private class PostEventTask extends AsyncTask<Object, Object, Object> {

		HttpResponse response;
		boolean isError = false;
		UserData userData = App.loadUserData();

		@Override
		protected Object doInBackground(Object... params) {
			Log.d(TAG, "doInBackground()");
			// send request
			try {
				JSONObject body = new JSONObject();
				body.put("action", "set_alarm");
				response = App.getConnector().event(userData.getUserId(), "USER", "PRIORITY_HIGH", 1, "NORMAL", body);
			} catch (IsaaCloudConnectionException e) {
				isError = true;
				e.printStackTrace();
			} catch (IOException e) {
				isError = true;
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		protected void onPostExecute(Object result) {
			Log.d(TAG, "onPostExecute()");
			// check for errors
			if (isError) {
				Log.d(TAG, "onPostExecute() - error detected");
			}
			if (response != null) {
				Log.d(TAG, "onPostExecute() - response: " + response.toString());
			}
		}

	}

	/**
	 * This is a helper class for managing the alarm list. It extends the
	 * ArrayAdapter class. For detailed information on how the ArrayAdapter
	 * class works, please check the class documentation provided by Google.
	 * 
	 * @author Mateusz Renes
	 * 
	 */
	private class AlarmAdapter extends ArrayAdapter<Alarm> {
		private final LayoutInflater mInflater;

		public AlarmAdapter(Context context) {
			super(context, R.layout.fragment_alarms_item);
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			UserData userData = App.loadUserData();
			for (Alarm alarm : userData.getAlarms()) {
				add(alarm);
			}
		}

		@Override
		public View getView(final int position, View convertView,
				ViewGroup parent) {
			View view;
			if (convertView == null) {
				view = mInflater.inflate(R.layout.fragment_alarms_item, parent,
						false);
			} else {
				view = convertView;
			}
			boolean isActive = getItem(position).isActive();
			Alarm alarm = (Alarm) getItem(position);
			TextView textDay = (TextView) view.findViewById(R.id.text_day);
			TextView textTime = (TextView) view.findViewById(R.id.text_time);
			textTime.setText(alarm.getTime());
			textDay.setText(alarm.getDay());
			if (!isActive) {
				view.setBackgroundColor(Color.GRAY);
				textTime.setText(R.string.time_not_set);
			} else {
				view.setBackgroundColor(Color.TRANSPARENT);
				textTime.setText(alarm.getTime());
			}
			return view;
		}
	}

}