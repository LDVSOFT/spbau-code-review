package net.ldvsoft.warofviruses;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

import static net.ldvsoft.warofviruses.GameLogic.BOARD_SIZE;
import static net.ldvsoft.warofviruses.GameLogic.PlayerFigure.CROSS;
import static net.ldvsoft.warofviruses.GameLogic.PlayerFigure.ZERO;

public class GameActivity extends GameActivityBase {
    private static Gson gson = new Gson();

    private TextView crossNick;
    private TextView zeroNick;
    private Button giveUpButton;
    private Button cancelTurnButton;
    private Button confirmTurnButton;
    private Button skipTurnButton;

    private BroadcastReceiver gameLoadedFromServerReceiver;
    private Game game = null;
    private static final long NO_GAME_SAVED = -1;
    private static final long DO_NOT_SAVE_GAME = -2;
    private static long lastSavedGameID = NO_GAME_SAVED; //it's somewhat a hack, but it's the simplest solution

    private final HumanPlayer.OnGameStateChangedListener ON_GAME_STATE_CHANGED_LISTENER =
            new HumanPlayer.OnGameStateChangedListener() {
                @Override
                public void onGameStateChanged(final GameLogic gameLogic) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            redrawGame(game);
                        }
                    });
                }
            };
    private HumanPlayer humanPlayer = new HumanPlayer(DBOpenHelper.getInstance(this).getUserById(HumanPlayer.USER_ANONYMOUS.getId()), CROSS,
            ON_GAME_STATE_CHANGED_LISTENER);

    private class OnExitActivityListener implements DialogInterface.OnClickListener {
        private boolean saveGame;
        private boolean giveUp;
        private boolean exitActivity;

        public OnExitActivityListener(boolean saveGame, boolean giveUp, boolean exitActivity) {
            this.saveGame = saveGame;
            this.giveUp = giveUp;
            this.exitActivity= exitActivity;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            lastSavedGameID = DO_NOT_SAVE_GAME;
            if (giveUp) {
                game.giveUp(humanPlayer);
            }
            if (saveGame) {
                saveCurrentGame();
            }
            if (exitActivity) {
                game = null;
                GameActivity.super.onBackPressed();
            }
        }
    }

    protected void redrawGame(Game game) {
        if (game == null) {
            return;
        }
        figureSet.setHueZero(game.getZeroPlayer().getUser().getColorZero());
        figureSet.setHueCross(game.getCrossPlayer().getUser().getColorCross());

        GameLogic displayState = game.getUnconfirmedGameLogic();
        boolean canMakeTurn = !game.isFinished() && game.getCurrentPlayer().equals(humanPlayer);
        super.redrawGame(displayState);
        crossNick.setText(game.getCrossPlayer().getName());
        zeroNick .setText(game.getZeroPlayer().getName());
        giveUpButton.setEnabled(!game.isFinished());
        skipTurnButton.setEnabled(!game.isFinished() && canMakeTurn && game.getUnconfirmedGameLogic() == game.getGameLogic());
        cancelTurnButton.setEnabled(game.getUnconfirmedGameLogic() != game.getGameLogic());
        confirmTurnButton.setEnabled(game.isWaitingForConfirm());
    }

    @Override
    public void onBackPressed() {
        if (game == null || game.isFinished()) {
            lastSavedGameID = DO_NOT_SAVE_GAME;
            saveCurrentGame();
            game = null;
            super.onBackPressed();
            return;
        }

        new AlertDialog.Builder(this)
                .setMessage("Do you want to save current game?")
                .setCancelable(false)
                .setPositiveButton("Yes, save and quit!", new OnExitActivityListener(true, false, true))
                .setNeutralButton("Cancel: I don't want to quit", new OnExitActivityListener(false, false, false))
                .setNegativeButton("I want to give up and quit", new OnExitActivityListener(true, true, true))
                        //.setNegativeButton("No, don't save it", new OnExitActivityListener(false, false)) probably I don't need this option...
                .show();
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        switch (getResources().getConfiguration().orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                findViewById(R.id.game_bar_replay).setVisibility(View.GONE);
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                findViewById(R.id.game_bar_replay_left ).setVisibility(View.GONE);
                findViewById(R.id.game_bar_replay_right).setVisibility(View.GONE);
                break;
        }

        crossNick   = (TextView) findViewById(R.id.game_cross_nick);
        zeroNick    = (TextView) findViewById(R.id.game_zero_nick);
        giveUpButton      = (Button) findViewById(R.id.game_button_giveup);
        cancelTurnButton  = (Button) findViewById(R.id.game_button_cancelturn);
        confirmTurnButton = (Button) findViewById(R.id.game_button_confirm);
        skipTurnButton    = (Button) findViewById(R.id.game_button_skipturn);

        game = new Game();

        Intent intent = getIntent();

        switch (intent.getIntExtra(WoVPreferences.OPPONENT_TYPE, -1)) {
            case WoVPreferences.OPPONENT_RESTORED_GAME:
                game = null; //will be loaded during onStart()
                break;
            case WoVPreferences.OPPONENT_BOT:
                game.startNewGame(humanPlayer, new AIPlayer(ZERO));
                break;
            case WoVPreferences.OPPONENT_LOCAL_PLAYER:
                //game.startNewGame(humanPlayer, new HumanPlayer(humanPlayer.getUser(), ZERO));
                game.startNewGame(humanPlayer, humanPlayer); //this is kind of hack since both players play for CROSS, but actually it doesn't matter
                break;
            case WoVPreferences.OPPONENT_NETWORK_PLAYER:
                loadGameFromJson(intent.getStringExtra(WoVPreferences.GAME_JSON_DATA));
                break;
            default:
                Log.wtf("GameActivityBase", "Could not start new game: incorrect opponent type");
        }
        initActivityStateAndListeners();
    }

    @Override
    protected void onPause() {
        Log.d("GameActivityBase", "onPause");
        if (game != null) {
            saveCurrentGame();
            game.onStop();
        }
        unregisterReceiver(gameLoadedFromServerReceiver);

        super.onPause();
    }

    private class OnSkipTurnListener implements View.OnClickListener {
        public void onClick(View v) {
            game.skipTurn(humanPlayer);
        }
    }

    private class OnGiveUpListener implements  View.OnClickListener {

        @Override
        public void onClick(View v) {
            game.giveUp(humanPlayer);
            redrawGame(game);
        }
    }

    private class OnCancelTurnListener implements  View.OnClickListener {

        @Override
        public void onClick(View v) {
            game.cancelTurn(humanPlayer);
            redrawGame(game);
        }
    }
    private class OnBoardClickListener implements View.OnClickListener {
        private final int x, y;

        OnBoardClickListener(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void onClick(View v) {
            game.doTurn(humanPlayer, x, y);
            redrawGame(game);
        }
    }

    private class OnConfirmListener implements  View.OnClickListener {

        @Override
        public void onClick(View v) {
            game.confirm(humanPlayer);
            redrawGame(game);
        }
    }

    //must be called AFTER creating/loading game to init everything properly
    private void initActivityStateAndListeners() {
        if (game == null) {
            return;
        }

        skipTurnButton.setOnClickListener(new OnSkipTurnListener());
        giveUpButton.setOnClickListener(new OnGiveUpListener());
        confirmTurnButton.setOnClickListener(new OnConfirmListener());
        cancelTurnButton.setOnClickListener(new OnCancelTurnListener());

        for (int i = 0; i != BOARD_SIZE; i++)
            for (int j = 0; j != BOARD_SIZE; j++) {
                boardButtons[i][j].setOnClickListener(new OnBoardClickListener(i, j));
            }

        if (game.getCrossPlayer() instanceof HumanPlayer) {
            ((HumanPlayer) game.getCrossPlayer()).setOnGameStateChangedListener(ON_GAME_STATE_CHANGED_LISTENER);
        }
        if (game.getZeroPlayer() instanceof HumanPlayer) {
            ((HumanPlayer) game.getZeroPlayer()).setOnGameStateChangedListener(ON_GAME_STATE_CHANGED_LISTENER);
        }

        redrawGame(game);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    protected void onResume() {
        super.onResume();
        new StoredGameLoader().execute();
        gameLoadedFromServerReceiver = new GameLoadedFromServerReceiver();
        registerReceiver(gameLoadedFromServerReceiver, new IntentFilter(WoVPreferences.MAIN_BROADCAST));
    }

    private void saveCurrentGame() {
        new AsyncTask<Game, Void, Void> (){
            @Override
            protected Void doInBackground(Game... params) {
                for (Game game : params) { //actually, there is only one game
                    long id = DBOpenHelper.getInstance(GameActivity.this).addGame(game);
                    if (lastSavedGameID != DO_NOT_SAVE_GAME) {
                        lastSavedGameID = id;
                    }
                }
                //}
                return null;
            }
        }.execute(game);
    }

    private final class StoredGameLoader extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            Game loadedGame = DBOpenHelper.getInstance(GameActivity.this).getAndRemoveActiveGame();
            if (loadedGame == null) {
                loadedGame = DBOpenHelper.getInstance(GameActivity.this).getGameById(lastSavedGameID);
                DBOpenHelper.getInstance(GameActivity.this).deleteGameById(lastSavedGameID);
            }
            lastSavedGameID = NO_GAME_SAVED;
            if (loadedGame == null) {
                Log.d("GameActivity", "FAIL: Null game loaded");
            } else {
                Log.d("GameActivity", "OK: game loaded");
                if (game != null) {
                    game.onStop();
                }
                game = loadedGame;
            }
            game.updateGameInfo();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (game != null) {
                onGameLoaded(game);
            }
        }
    }

    private class GameLoadedFromServerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("GameActivity", "networkLoadGame broadcast recieved!");
            Bundle tmp = intent.getBundleExtra(WoVPreferences.BUNDLE);
            String data = tmp.getString(WoVProtocol.DATA);
            loadGameFromJson(data);
        }
    }

    private void loadGameFromJson(String data) {
        JsonObject jsonData = (JsonObject) new JsonParser().parse(data);
        User cross = gson.fromJson(jsonData.get(WoVProtocol.CROSS_USER), User.class);
        User zero = gson.fromJson(jsonData.get(WoVProtocol.ZERO_USER), User.class);

        DBOpenHelper.getInstance(GameActivity.this).addUser(cross);
        DBOpenHelper.getInstance(GameActivity.this).addUser(zero);

        GameLogic.PlayerFigure myFigure = gson.fromJson(jsonData.get(WoVProtocol.MY_FIGURE),
                GameLogic.PlayerFigure.class);
        Player playerCross, playerZero;

        switch (myFigure) {
            case CROSS:
                playerZero = new ClientNetworkPlayer(cross, ZERO, GameActivity.this);
                playerCross = humanPlayer = new HumanPlayer(zero, CROSS);
                break;
            case ZERO:
                playerCross = new ClientNetworkPlayer(cross, CROSS, GameActivity.this);
                playerZero = humanPlayer = new HumanPlayer(zero, ZERO);
                break;
            default:
                throw new IllegalArgumentException("Illegal myFigure value!");
        }

        List<GameEvent> events = WoVProtocol.eventsFromJson(jsonData);

        humanPlayer.setOnGameStateChangedListener(ON_GAME_STATE_CHANGED_LISTENER);
        int crossType = myFigure == CROSS ? 0 : 2;
        int zeroType = 2 - crossType; //fixme remove magic constants
        if (game != null) {
            game.onStop();
        }
        game = Game.deserializeGame(gson.fromJson(jsonData.get(WoVProtocol.GAME_ID), int.class),
                playerCross, crossType, playerZero, zeroType, GameLogic.deserialize(events));
        initActivityStateAndListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void onGameLoaded(Game game) {
        this.game = game;
        initActivityStateAndListeners();
    }
}
