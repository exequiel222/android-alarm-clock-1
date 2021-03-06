package pl.sointeractive.isaaclock.activities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.json.JSONException;
import org.json.JSONObject;

import pl.sointeractive.isaaclock.R;
import pl.sointeractive.isaaclock.data.App;
import pl.sointeractive.isaaclock.data.UserData;
import pl.sointeractive.isaaclock.fragments.AchievementsFragment;
import pl.sointeractive.isaaclock.fragments.AlarmsFragment;
import pl.sointeractive.isaaclock.fragments.GeneralFragment;
import pl.sointeractive.isaaclock.fragments.LeaderboardFragment;
import pl.sointeractive.isaaclock.fragments.NotificationsFragment;
import pl.sointeractive.isaacloud.connection.HttpResponse;
import pl.sointeractive.isaacloud.exceptions.IsaaCloudConnectionException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.widget.TabHost;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Main activity of the application. This class stores user tabs that allow the
 * user to view their general data, set alarms, view achievements, leaderboard
 * and notifications. The class is heavily based on the example TabView class
 * from the ActionBarSherlock library.
 * 
 * @author Mateusz Renes
 * 
 */
public class UserActivity extends SherlockFragmentActivity {

	private static final String TAG = "UserActivity";
	private static final int RESULT_SETTINGS = 1;

	private TabHost mTabHost;
	private static TabManager mTabManager;

	/**
	 * Method used on activity creation. Here the tabs are initiated, but not
	 * filled with their individual data.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_user_tabs);
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabHost.setup();
		mTabManager = new TabManager(this, mTabHost, R.id.realtabcontent);
		mTabManager.addTab(
				mTabHost.newTabSpec("general").setIndicator(null,
						getResources().getDrawable(R.drawable.ic_menu_cc_am)),
				GeneralFragment.class, null);
		mTabManager.addTab(
				mTabHost.newTabSpec("alarms").setIndicator(
						null,
						getResources().getDrawable(
								R.drawable.ic_menu_recent_history)),
				AlarmsFragment.class, null);
		mTabManager.addTab(
				mTabHost.newTabSpec("achievements").setIndicator(null,
						getResources().getDrawable(R.drawable.ic_menu_star)),
				AchievementsFragment.class, null);
		mTabManager.addTab(
				mTabHost.newTabSpec("leaderboard").setIndicator(
						null,
						getResources().getDrawable(
								R.drawable.ic_menu_friendslist)),
				LeaderboardFragment.class, null);
		mTabManager.addTab(
				mTabHost.newTabSpec("notification").setIndicator(
						null,
						getResources().getDrawable(
								R.drawable.ic_menu_notifications)),
				NotificationsFragment.class, null);
		if (savedInstanceState != null) {
			mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
		}
		getMenuInflater();
		new PostEventTask().execute();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.logout:
			logout();
			return true;
		case R.id.settings:
			openSettings();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void logout() {
		this.finish();
	}

	public void openSettings() {
		Intent i = new Intent(this, SettingsActivity.class);
		startActivityForResult(i, RESULT_SETTINGS);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case RESULT_SETTINGS:
			saveSettings();
			break;
		}
	}

	/**
	 * Save user preferences to the UserData object.
	 */
	private void saveSettings() {
		Log.d(TAG, "saveSettings()");
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		UserData userData = App.loadUserData();
		boolean isUsing24hTime = prefs.getBoolean("pref24hTime", false);
		userData.setUse24HourTime(isUsing24hTime);
		mTabManager.refreshTab(mTabHost.getCurrentTabTag());
		App.saveUserData(userData);
	}

	public TabHost getTabHost() {
		return mTabHost;
	}

	public TabManager getTabManager() {
		return mTabManager;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("tab", mTabHost.getCurrentTabTag());
	}

	/**
	 * This is a modified version of the ABS library class with the same name.
	 * The main differrence is that the tabs content is refreshed on every tab
	 * change. The original class description is below:
	 * 
	 * This is a helper class that implements a generic mechanism for
	 * associating fragments with the tabs in a tab host. It relies on a trick.
	 * Normally a tab host has a simple API for supplying a View or Intent that
	 * each tab will show. This is not sufficient for switching between
	 * fragments. So instead we make the content part of the tab host 0dp high
	 * (it is not shown) and the TabManager supplies its own dummy view to show
	 * as the tab content. It listens to changes in tabs, and takes care of
	 * switch to the correct fragment shown in a separate content area whenever
	 * the selected tab changes.
	 */
	public static class TabManager implements TabHost.OnTabChangeListener {
		private final FragmentActivity mActivity;
		private final TabHost mTabHost;
		private final int mContainerId;
		private final HashMap<String, TabInfo> mTabs = new HashMap<String, TabInfo>();
		TabInfo mLastTab;

		static final class TabInfo {
			private final String tag;
			private final Class<?> clss;
			private final Bundle args;
			private Fragment fragment;

			TabInfo(String _tag, Class<?> _class, Bundle _args) {
				tag = _tag;
				clss = _class;
				args = _args;
			}
		}

		public void printTabInfo() {
			for (Entry<String, TabInfo> e : mTabs.entrySet()) {
				System.out.println(e.getKey() + " " + e.getValue().tag + " ");
			}
		}

		static class DummyTabFactory implements TabHost.TabContentFactory {
			private final Context mContext;

			public DummyTabFactory(Context context) {
				mContext = context;
			}

			@Override
			public View createTabContent(String tag) {
				View v = new View(mContext);
				v.setMinimumWidth(0);
				v.setMinimumHeight(0);
				return v;
			}
		}

		public TabManager(FragmentActivity activity, TabHost tabHost,
				int containerId) {
			mActivity = activity;
			mTabHost = tabHost;
			mContainerId = containerId;
			mTabHost.setOnTabChangedListener(this);
		}

		public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
			tabSpec.setContent(new DummyTabFactory(mActivity));
			String tag = tabSpec.getTag();
			TabInfo info = new TabInfo(tag, clss, args);
			// Check to see if we already have a fragment for this tab, probably
			// from a previously saved state. If so, deactivate it, because our
			// initial state is that a tab isn't shown.
			info.fragment = mActivity.getSupportFragmentManager()
					.findFragmentByTag(tag);
			if (info.fragment != null && !info.fragment.isDetached()) {
				FragmentTransaction ft = mActivity.getSupportFragmentManager()
						.beginTransaction();
				ft.detach(info.fragment);
				ft.commit();
			}
			mTabs.put(tag, info);
			mTabHost.addTab(tabSpec);
		}

		public void refreshTab(String tabId) {
			Log.d(TAG, " refreshTab, tabId: " + tabId);
			TabInfo newTab = mTabs.get(tabId);
			Log.d(TAG, "newTab.tag: " + newTab.tag);
			FragmentTransaction ft = mActivity.getSupportFragmentManager()
					.beginTransaction();
			if (mLastTab != null) {
				if (mLastTab.fragment != null) {
					Log.d(TAG, " refreshTab, detach ");
					ft.detach(mLastTab.fragment);
				}
			}
			if (newTab != null) {
				if (newTab.fragment == null) {
					newTab.fragment = Fragment.instantiate(mActivity,
							newTab.clss.getName(), newTab.args);
					Log.d(TAG, " refreshTab, add ");
					ft.add(mContainerId, newTab.fragment, newTab.tag);
				} else {
					Log.d(TAG, " refreshTab, attach ");
					ft.attach(newTab.fragment);
				}
			}
			mLastTab = newTab;
			ft.commit();
			mActivity.getSupportFragmentManager().executePendingTransactions();
		}

		@Override
		public void onTabChanged(String tabId) {
			Log.d(TAG, "onTabChanged, tabId: " + tabId);
			TabInfo newTab = mTabs.get(tabId);
			Log.d(TAG, "newTab.tag: " + newTab.tag);
			FragmentTransaction ft = mActivity.getSupportFragmentManager()
					.beginTransaction();
			if (mLastTab != null) {
				if (mLastTab.fragment != null) {
					ft.detach(mLastTab.fragment);
				}
			}
			if (newTab != null) {
				newTab.fragment = Fragment.instantiate(mActivity,
						newTab.clss.getName(), newTab.args);
				ft.add(mContainerId, newTab.fragment, newTab.tag);
			}
			mLastTab = newTab;
			ft.commit();
			mActivity.getSupportFragmentManager().executePendingTransactions();
			mTabManager.refreshTab(mTabHost.getCurrentTabTag());
		}
	}

	@Override
	public void onBackPressed() {
		// do nothing
	}

	/**
	 * AsyncTask used to send an appropriate event to the API when the user
	 * visits their account.
	 * 
	 * @author Mateusz Renes
	 */
	private class PostEventTask extends AsyncTask<Object, Object, Object> {

		HttpResponse response;
		boolean isError = false;
		UserData userData = App.loadUserData();

		@Override
		protected Object doInBackground(Object... params) {
			Log.d(TAG, "doInBackground()");
			try {
				JSONObject body = new JSONObject();
				body.put("action", "create_account");
				response = App.getConnector().event(userData.getUserId(),
						"USER", "PRIORITY_HIGH", 1, "NORMAL", body);
			} catch (IsaaCloudConnectionException e) {
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
			if (isError) {
				Log.d(TAG, "onPostExecute() - error detected");
			}
			if (response != null) {
				Log.d(TAG, "onPostExecute() - response: " + response.toString());
			}
		}

	}
}
