package net.programmierecke.radiodroid2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import net.programmierecke.radiodroid2.interfaces.IFragmentRefreshable;
import net.programmierecke.radiodroid2.interfaces.IFragmentSearchable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class ActivityMain extends AppCompatActivity implements SearchView.OnQueryTextListener, IMPDClientStatusChange {

    public static final String EXTRA_SEARCH_TAG = "search_tag";

    private static final String TAG = "RadioDroid";

    private final String TAG_SEARCH_URL = "https://www.radio-browser.info/webservice/json/stations/bytagexact";
    private final String SAVE_LAST_MENU_ITEM = "LAST_MENU_ITEM";

    private SearchView mSearchView;

    DrawerLayout mDrawerLayout;
    NavigationView mNavigationView;
    FragmentManager mFragmentManager;

    IFragmentRefreshable fragRefreshable = null;
    IFragmentSearchable fragSearchable = null;

    MenuItem menuItemSearch;
    MenuItem menuItemRefresh;

    private SharedPreferences sharedPref;

    private int selectedMenuItem = -1;

    @Override
    public void changed() {
        Handler mainHandler = new Handler(getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                invalidateOptionsMenu();
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);

        if (savedInstanceState != null) {
            selectedMenuItem = savedInstanceState.getInt(SAVE_LAST_MENU_ITEM, -1);
        }

        File dir = new File(getFilesDir().getAbsolutePath());
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                if (BuildConfig.DEBUG) {
                    Log.d("MAIN", "delete file:" + aChildren);
                }
                if (!new File(dir, aChildren).delete()) {
                    Log.w("MAIN", "Failed to delete " + new File(dir, aChildren));
                }
            }
        }

        final Toolbar myToolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(myToolbar);

        PlayerServiceUtil.bind(this);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mFragmentManager = getSupportFragmentManager();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        mNavigationView = (NavigationView) findViewById(R.id.my_navigation_view);

        mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                mDrawerLayout.closeDrawers();
                android.support.v4.app.Fragment f = null;

                if (menuItem.getItemId() == R.id.nav_item_player_status) {
                    Intent intent = new Intent(ActivityMain.this, ActivityPlayerInfo.class);
                    startActivity(intent);
                    return false;
                }

                if (menuItem.getItemId() == R.id.nav_item_stations) {
                    f = new FragmentTabs();
                    menuItemSearch.setVisible(true);
                    myToolbar.setTitle(R.string.app_name);
                }

                if (menuItem.getItemId() == R.id.nav_item_starred) {
                    f = new FragmentStarred();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_starred);
                }

                if (menuItem.getItemId() == R.id.nav_item_history) {
                    f = new FragmentHistory();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_history);
                }

                if (menuItem.getItemId() == R.id.nav_item_serverinfo) {
                    f = new FragmentServerInfo();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_statistics);
                }

                if (menuItem.getItemId() == R.id.nav_item_recordings) {
                    f = new FragmentRecordings();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_recordings);
                }

                if (menuItem.getItemId() == R.id.nav_item_alarm) {
                    f = new FragmentAlarm();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_alarm);
                }

                if (menuItem.getItemId() == R.id.nav_item_settings) {
                    f = new FragmentSettings();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_settings);
                }

                if (menuItem.getItemId() == R.id.nav_item_about) {
                    f = new FragmentAbout();
                    menuItemSearch.setVisible(false);
                    myToolbar.setTitle(R.string.nav_item_about);
                }

                FragmentTransaction xfragmentTransaction = mFragmentManager.beginTransaction();
                xfragmentTransaction.replace(R.id.containerView, f).commit();
                fragRefreshable = null;
                fragSearchable = null;
                if (f instanceof IFragmentRefreshable) {
                    fragRefreshable = (IFragmentRefreshable) f;
                }
                if (f instanceof IFragmentSearchable) {
                    fragSearchable = (IFragmentSearchable) f;
                }
                menuItemRefresh.setVisible(fragRefreshable != null);

                menuItem.setChecked(true);
                selectedMenuItem = menuItem.getItemId();

                return false;
            }
        });

        //myToolbar = (android.support.v7.widget.Toolbar) findViewById(R.id.main_toolbar);
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.app_name, R.string.app_name);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();

        CastHandler.onCreate(this);

        MPDClient.StartDiscovery(this, this);
    }

	@Override
    protected void onNewIntent(Intent intent) {
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        final String searchTag = extras.getString(EXTRA_SEARCH_TAG);
        if (searchTag != null) {
            try {
                String queryEncoded = URLEncoder.encode(searchTag, "utf-8");
                queryEncoded = queryEncoded.replace("+", "%20");
                Search(TAG_SEARCH_URL + "/" + queryEncoded);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
	public void onRequestPermissionsResult(int requestCode,
										  @NonNull String permissions[], @NonNull int[] grantResults) {
		if(BuildConfig.DEBUG) { Log.d(TAG,"on request permissions result:"+requestCode);
		}switch (requestCode) {
			case Utils.REQUEST_EXTERNAL_STORAGE: {
				// If request is cancelled, the result arrays are empty.
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					if (fragRefreshable != null){
						if(BuildConfig.DEBUG) { Log.d(TAG,"REFRESH VIEW"); }
						fragRefreshable.Refresh();
					}
				} else {
					Toast toast = Toast.makeText(this, getResources().getString(R.string.error_record_needs_write), Toast.LENGTH_SHORT);
					toast.show();
				}

			}
		}
	}

    @Override
    public void onDestroy() {
        super.onDestroy();
        PlayerServiceUtil.unBind(this);
        MPDClient.StopDiscovery();
    }

    @Override
    protected void onPause() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "PAUSED");
        }
        super.onPause();
        CastHandler.onPause();
        MPDClient.StopDiscovery();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(SAVE_LAST_MENU_ITEM, selectedMenuItem);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        menuItemSearch = menu.findItem(R.id.action_search);
        mSearchView = (SearchView) MenuItemCompat.getActionView(menuItemSearch);
        mSearchView.setOnQueryTextListener(this);

        menuItemRefresh = menu.findItem(R.id.action_refresh);
        MenuItem menuItemMPDNok = menu.findItem(R.id.action_mpd_nok);
        MenuItem menuItemMPDOK = menu.findItem(R.id.action_mpd_ok);

        if (fragSearchable == null) {
            menuItemSearch.setVisible(false);
        }

        if (fragRefreshable == null) {
            menuItemRefresh.setVisible(false);
        }

        menuItemMPDOK.setVisible(MPDClient.Discovered() && MPDClient.Connected());
        menuItemMPDNok.setVisible(MPDClient.Discovered() && !MPDClient.Connected());

        MenuItem mediaRouteMenuItem = CastHandler.getRouteItem(getApplicationContext(), menu);

        if (selectedMenuItem < 0) {
            final String startupAction = sharedPref.getString("startup_action", getResources().getString(R.string.startup_show_all_stations));
            if (startupAction.equals(getResources().getString(R.string.startup_show_favorites))) {
                selectMenuItem(R.id.nav_item_starred);
            } else if (startupAction.equals(getResources().getString(R.string.startup_show_history))) {
                selectMenuItem(R.id.nav_item_history);
            } else {
                selectMenuItem(R.id.nav_item_stations);
            }
        } else {
            selectMenuItem(selectedMenuItem);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);  // OPEN DRAWER
                return true;
            case R.id.action_refresh:
                if (fragRefreshable != null) {
                    fragRefreshable.Refresh();
                }
                return true;
            case R.id.action_mpd_nok:
                MPDClient.Connect(this);
                return true;
            case R.id.action_mpd_ok:
                MPDClient.Disconnect(this, this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sharedPref == null) {
            sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            CastHandler.onResume();

            Log.i(TAG, "RESUMED");
            MPDClient.StartDiscovery(this, this);
        }
    }

    public void Search(String query) {
        selectMenuItem(R.id.nav_item_stations);

        if (fragSearchable != null) {
            fragSearchable.Search(query);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        String queryEncoded;
        try {
            mSearchView.setQuery("", false);
            mSearchView.clearFocus();
            mSearchView.setIconified(true);
            queryEncoded = URLEncoder.encode(query, "utf-8");
            queryEncoded = queryEncoded.replace("+", "%20");
            Search("https://www.radio-browser.info/webservice/json/stations/byname/" + queryEncoded);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    private void selectMenuItem(int itemId) {
        mNavigationView.setCheckedItem(itemId);
        mNavigationView.getMenu().performIdentifierAction(itemId, 0);
    }
}
