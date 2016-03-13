package net.ldvsoft.warofviruses;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

public class MenuActivity extends AppCompatActivity {
    private static final Gson gson = new Gson();
    private static final int RC_SIGN_IN = 9000;
    private static final IntentFilter INTENT = new IntentFilter(WoVPreferences.MAIN_BROADCAST);

    private GcmMessagesReceiver gcmMessagesReceiver = null;
    private TextView userName;
    private BoardCellButton crossButton;
    private BoardCellButton zeroButton;
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private Button playAgainstBotButton;
    private Button playAgainstLocalPlayerButton;
    private Button playOnlineButton;
    private Button restoreGameButton;
    /*FIXME*/
    private FigureSet figureSet = new FigureSet();

    private GoogleApiClient apiClient;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        GoogleCloudMessaging.getInstance(this); // Can this help?...
        setContentView(R.layout.activity_menu);
        SharedPreferences preferences = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE);
        if (!preferences.contains(WoVPreferences.CURRENT_USER_ID)) {
            preferences.edit().putLong(WoVPreferences.CURRENT_USER_ID, HumanPlayer.USER_ANONYMOUS.getId()).apply();
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        playAgainstBotButton = (Button) findViewById(R.id.button_play_against_bot);
        playAgainstLocalPlayerButton = (Button) findViewById(R.id.button_play_against_player);
        playOnlineButton = (Button) findViewById(R.id.button_play_online);
        restoreGameButton = (Button) findViewById(R.id.button_restore_game);

        navigationView = (NavigationView) findViewById(R.id.navigation_view);
        View drawerHeader = navigationView.inflateHeaderView(R.layout.drawer_header);
        crossButton = (BoardCellButton) drawerHeader.findViewById(R.id.avatar_cross);
        zeroButton = (BoardCellButton) drawerHeader.findViewById(R.id.avatar_zero);
        userName = (TextView) drawerHeader.findViewById(R.id.username);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.drawer_login:
                        logIn();
                        drawerLayout.closeDrawers();
                        return true;
                    case R.id.drawer_logout:
                        logOut();
                        drawerLayout.closeDrawers();
                        return true;
                    case R.id.drawer_clear_db:
                        clearDB();
                        return true;
                    case R.id.drawer_settings:
                        Intent intent = new Intent(MenuActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        return true;
                    case R.id.drawer_ping:
                        WoVGcmListenerService.sendGcmMessage(MenuActivity.this, WoVProtocol.ACTION_PING, null);
                        drawerLayout.closeDrawers();
                        return true;
                    default:
                        Toast.makeText(MenuActivity.this, menuItem.getTitle() + " pressed", Toast.LENGTH_LONG).show();
                        drawerLayout.closeDrawers();
                        return true;
                }
            }
        });

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestProfile()
                .requestIdToken(getString(R.string.server_client_id))
                .build();
        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        apiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Toast.makeText(MenuActivity.this, "No Google?", Toast.LENGTH_SHORT).show();
                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        gcmMessagesReceiver = new GcmMessagesReceiver();
    }

    @Override
    protected void onStart() {
        super.onStart();

        OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(apiClient);
        if (opr.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            hideProgressDialog();
            GoogleSignInResult result = opr.get();
            handleSignInResult(result);
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            showProgressDialog(getString(R.string.menu_logging_back));
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(GoogleSignInResult googleSignInResult) {
                    hideProgressDialog();
                    handleSignInResult(googleSignInResult);
                }
            });
        }

        registerReceiver(gcmMessagesReceiver, INTENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(gcmMessagesReceiver);
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void playAgainstBot(View view) {
        Intent intent = new Intent(MenuActivity.this, GameActivity.class);
        intent.putExtra(WoVPreferences.OPPONENT_TYPE, WoVPreferences.OPPONENT_BOT);
        startActivity(intent);
    }

    public void playAgainstLocalPlayer(View view) {
        Intent intent = new Intent(MenuActivity.this, GameActivity.class);
        intent.putExtra(WoVPreferences.OPPONENT_TYPE, WoVPreferences.OPPONENT_LOCAL_PLAYER);
        startActivity(intent);
    }

    public void viewGameHistory(View view) {
        Intent intent = new Intent(this, GameHistoryActivity.class);
        startActivity(intent);
    }

    private class GcmMessagesReceiver extends BroadcastReceiver {
        private boolean waitForGame = false;
        private boolean waitForLogin = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle tmp = intent.getBundleExtra(WoVPreferences.BUNDLE);
            String data = tmp.getString(WoVProtocol.DATA);
            String action = tmp.getString(WoVProtocol.ACTION);
            assert action != null && data != null;
            switch (action) {
                case WoVProtocol.GAME_LOADED:
                    if (!waitForGame) {
                        break;
                    }
                    Log.d("GameActivity", "networkLoadGame broadcast recieved!");
                    intent = new Intent(MenuActivity.this, GameActivity.class);
                    intent.putExtra(WoVPreferences.OPPONENT_TYPE, WoVPreferences.OPPONENT_NETWORK_PLAYER);
                    intent.putExtra(WoVPreferences.GAME_JSON_DATA, data);
                    waitForGame = false;
                    startActivity(intent);
                    break;
                case WoVProtocol.ACTION_LOGIN_COMPLETE:
                    if (!waitForLogin) {
                        break;
                    }
                    waitForLogin = false;
                    JsonObject jsonData = (JsonObject) new JsonParser().parse(data);
                    if (jsonData.get(WoVProtocol.RESULT).getAsString().equals(WoVProtocol.RESULT_FAILURE)) {
                        hideProgressDialog();
                        Toast.makeText(MenuActivity.this, "Login failed by server.", Toast.LENGTH_SHORT).show();
                    } else {
                        User newUser = gson.fromJson(jsonData.get(WoVProtocol.USER), User.class);
                        SharedPreferences preferences = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE);
                        preferences.edit().putLong(WoVPreferences.CURRENT_USER_ID, newUser.getId()).apply();
                        DBOpenHelper.getInstance(MenuActivity.this).addUser(newUser);
                        updateUI();
                        hideProgressDialog();
                    }
                    break;
                case WoVProtocol.ACTION_PING:
                    Toast.makeText(MenuActivity.this, "PING returned!", Toast.LENGTH_LONG).show();
                    break;
            }
        }
    }

    private void logIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(apiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void logOut() {
        Auth.GoogleSignInApi.signOut(apiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        SharedPreferences preferences = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE);
                        preferences.edit().putLong(WoVPreferences.CURRENT_USER_ID, HumanPlayer.USER_ANONYMOUS.getId()).apply();
                        new AsyncTask<Void, Void, Void>() {
                            @Override
                            protected Void doInBackground(Void... params) {
                                try {
                                    InstanceID instanceID = InstanceID.getInstance(MenuActivity.this);
                                    instanceID.deleteToken(getString(R.string.gcm_defaultSenderId), "GCM");
                                } catch (IOException e) {
                                    Log.w(MenuActivity.class.getName(), "Error while deleting token", e);
                                }
                                return null;
                            }
                        }.execute();
                        updateUI();
                        hideProgressDialog();
                    }
                });
    }

    public void playOnline(View view) {
        WoVGcmListenerService.sendGcmMessage(MenuActivity.this, WoVProtocol.ACTION_USER_READY, new JsonObject());
        gcmMessagesReceiver.waitForGame = true;
    }

    public void clearDB() {
        DBOpenHelper instance = DBOpenHelper.getInstance(this);
        instance.onUpgrade(instance.getReadableDatabase(), 0, 0);
        updateUI();
    }

    public void restoreSavedGame(View view) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(WoVPreferences.OPPONENT_TYPE, WoVPreferences.OPPONENT_RESTORED_GAME);
        startActivity(intent);
    }

    public void updateUI() {
        boolean hasGame = DBOpenHelper.getInstance(this).hasActiveGame();
        long userId = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE).getLong(WoVPreferences.CURRENT_USER_ID, -1);
        User user = DBOpenHelper.getInstance(this).getUserById(userId);
        if (user == null) {
            Toast.makeText(MenuActivity.this, "Credentials corrupted, logging out", Toast.LENGTH_SHORT).show();
            logOut();
            return;
        }
        boolean isOnline = user.getId() != HumanPlayer.USER_ANONYMOUS.getId();

        playAgainstBotButton.setEnabled(!hasGame);
        playAgainstLocalPlayerButton.setEnabled(!hasGame);
        playOnlineButton.setEnabled(!hasGame && isOnline);
        restoreGameButton.setEnabled(hasGame);

        navigationView.getMenu().findItem(R.id.drawer_login).setEnabled(!isOnline);
        navigationView.getMenu().findItem(R.id.drawer_logout).setEnabled(isOnline);

        /*FIXME*/
        for (GameLogic.PlayerFigure figure : GameLogic.PlayerFigure.values()) {
            figureSet.setFigureSource(figure, DefaultFigureSource.NAME);
        }
        userName.setText(user.getFullNickname());
        figureSet.setHueCross(user.getColorCross());
        figureSet.setHueZero(user.getColorZero());
        crossButton.setFigure(figureSet, BoardCellState.get(GameLogic.CellType.CROSS));
        zeroButton.setFigure(figureSet, BoardCellState.get(GameLogic.CellType.ZERO));
    }

    private void handleSignInResult(final GoogleSignInResult result) {
        Log.d("MainActivity", "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        InstanceID instanceID = InstanceID.getInstance(MenuActivity.this);
                        instanceID.getToken(getString(R.string.gcm_defaultSenderId), "GCM");
                    } catch (IOException e) {
                        Log.w(MenuActivity.class.getName(), "Error while getting token", e);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    // Signed in successfully, show authenticated UI.
                    GoogleSignInAccount acct = result.getSignInAccount();
                    String token = acct.getIdToken();

                    long userId = getSharedPreferences(WoVPreferences.PREFERENCES, MODE_PRIVATE).getLong(WoVPreferences.CURRENT_USER_ID, -1);
                    User user = DBOpenHelper.getInstance(MenuActivity.this).getUserById(userId);

                    JsonObject data = new JsonObject();
                    data.addProperty(WoVProtocol.GOOGLE_TOKEN, token);
                    data.add(WoVProtocol.LOCAL_USER, gson.toJsonTree(user));
                    gcmMessagesReceiver.waitForLogin = true;
                    WoVGcmListenerService.sendGcmMessage(MenuActivity.this, WoVProtocol.ACTION_LOGIN, data);
                    showProgressDialog(getString(R.string.menu_connecting_to_server));
                    //Actual UI changes will come on message result
                }
            }.execute();
        } else {
            WoVGcmListenerService.sendGcmMessage(this, WoVProtocol.ACTION_LOGOUT, null);
            updateUI();
        }
    }

    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage(message);
            progressDialog.setIndeterminate(true);
            /* FIXME progressDialog.setCancelable(false); */
        }

        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.hide();
        }
    }
}
