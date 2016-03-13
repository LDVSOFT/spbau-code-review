package net.ldvsoft.warofviruses;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Created by Сева on 04.11.2015.
 */
public class DBOpenHelper extends SQLiteOpenHelper implements DBProvider {
    private static final String TAG = "DBHelper";

    private static final int VERSION = 19;
    private static final String DB_NAME = "gameHistoryDB";

    private static final String CREATE_GAME_TABLE = "CREATE TABLE " + GAME_TABLE + "(" + ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            PLAYER_CROSSES + " INTEGER UNSIGNED NOT NULL, " + PLAYER_CROSSES_TYPE + " INT UNSIGNED NOT NULL, " +
            PLAYER_ZERO + " INTEGER UNSIGNED NOT NULL, " + PLAYER_ZEROES_TYPE + " INT UNSIGNED NOT NULL, " + GAME_STATUS +
            " INT NOT NULL, " + GAME_DATE + " TEXT NOT NULL, FOREIGN KEY (" + PLAYER_CROSSES + ", " + PLAYER_ZERO +
            ") REFERENCES " + USER_TABLE + " (" + ID + ", " + ID + ") ON DELETE CASCADE ON UPDATE CASCADE);";

    private static final String CREATE_TURN_TABLE = "CREATE TABLE " + TURN_TABLE + "(" + GAME_ID + " INTEGER UNSIGNED NOT NULL, " +
            TURN_NUMBER + " INT UNSIGNED NOT NULL, " + TURN_TYPE + " INT NOT NULL, " + TURN_X + " INT NULL, " + TURN_Y + " INT NULL, " +
            "PRIMARY KEY(" + GAME_ID + ", " + TURN_NUMBER + "), FOREIGN KEY (" + GAME_ID + ") REFERENCES " + GAME_TABLE + "(" + ID + ")" +
            "ON DELETE CASCADE ON UPDATE CASCADE);";

    private static final String CREATE_USER_TABLE = "CREATE TABLE " + USER_TABLE + "(" + ID + " INTEGER, " + GOOGLE_TOKEN +
            " TEXT NOT NULL UNIQUE, " + NICKNAME_STR + " TEXT NOT NULL, " + NICKNAME_ID +
            " INT UNSIGNED NOT NULL, " + COLOR_CROSS + " INT UNSIGNED NOT NULL, " + COLOR_ZERO + " INT UNSIGNED NOT NULL, " +
            INVITATION_TARGET + " INTEGER NULL, PRIMARY KEY (" + ID + "), FOREIGN KEY (" + INVITATION_TARGET + ") REFERENCES " +
            USER_TABLE + " (" + ID + ") ON DELETE CASCADE ON UPDATE CASCADE);";

    private static DBOpenHelper instance;
    private Context context;

    private static final String DROP_GAME_TABLE = "DROP TABLE IF EXISTS " + GAME_TABLE + ";";
    private static final String DROP_TURN_TABLE = "DROP TABLE IF EXISTS " + TURN_TABLE + ";";
    private static final String DROP_USER_TABLE = "DROP TABLE IF EXISTS " + USER_TABLE + ";";

    private static final Class<?>[] playerClasses = {HumanPlayer.class, AIPlayer.class, ClientNetworkPlayer.class};

    public synchronized static DBOpenHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBOpenHelper(context);
            instance.context = context;
        }
        return instance;
    }

    private DBOpenHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_GAME_TABLE);
        db.execSQL(CREATE_TURN_TABLE);
        db.execSQL(CREATE_USER_TABLE);
        addUser(HumanPlayer.USER_ANONYMOUS, db);
        addUser(AIPlayer.AI_USER, db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_GAME_TABLE);
        db.execSQL(DROP_TURN_TABLE);
        db.execSQL(DROP_USER_TABLE);
        onCreate(db);
    }

    @Override
    public long addGame(Game game) {
        ContentValues cv = new ContentValues();
        long gameId = new SecureRandom().nextLong();
        switch (game.getGameState()) {
            case NOT_RUNNING:
            case RUNNING:
                cv.put(GAME_STATUS, GameStatus.RUNNING.ordinal());
                break;
            case DRAW:
                cv.put(GAME_STATUS, GameStatus.FINISHED_DRAW.ordinal());
                break;
            case CROSS_WON:
                cv.put(GAME_STATUS, GameStatus.FINISHED_CROSS_WON.ordinal());
                break;
            case ZERO_WON:
                cv.put(GAME_STATUS, GameStatus.FINISHED_ZERO_WON.ordinal());
                break;
        }
        //cv.put(GAME_STATUS, game.isFinished() ? GameStatus.FINISHED.ordinal() : GameStatus.RUNNING.ordinal());
        cv.put(PLAYER_CROSSES, game.getCrossPlayer().getUser().getId());
        cv.put(PLAYER_ZERO, game.getZeroPlayer().getUser().getId());
        cv.put(ID, gameId);
        cv.put(PLAYER_CROSSES_TYPE, game.getCrossType());
        cv.put(PLAYER_ZEROES_TYPE, game.getZeroType());

        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String formattedDate = df.format(c.getTime());
        cv.put(GAME_DATE, formattedDate);
        getWritableDatabase().insert(GAME_TABLE, null, cv);

        List<GameEvent> eventHistory = game.getGameLogic().getEventHistory();
        for (int i = 0; i < eventHistory.size(); i++) {
            cv = new ContentValues();
            cv.put(GAME_ID, gameId);
            cv.put(TURN_NUMBER, i);
            GameEvent event = eventHistory.get(i);
            cv.put(TURN_X, event.getTurnX());
            cv.put(TURN_Y, event.getTurnY());
            cv.put(TURN_TYPE, event.getEventTypeAsInt());
            getWritableDatabase().insert(TURN_TABLE, null, cv);
        }
        return gameId;
    }

    public Game getActiveGame() {
        Cursor gameCursor = getReadableDatabase().rawQuery(GET_ACTIVE_GAME, null);
        Cursor turnsCursor = getReadableDatabase().rawQuery(GET_ACTIVE_GAME_TURNS, null);

        Log.d(TAG, "Loading active game: found " + gameCursor.getCount() + " games and " + turnsCursor.getCount() + " turns");

        Game game = getGameFromCursors(gameCursor, turnsCursor);
        gameCursor.close();
        turnsCursor.close();
        return game;
    }

    public void deleteActiveGame() {
        getWritableDatabase().execSQL(DELETE_ACTIVE_GAME_TURNS);
        getWritableDatabase().execSQL(DELETE_ACTIVE_GAME);
    }

    public ArrayList<Game> getGameHistory() {
        Cursor cursor = getReadableDatabase().rawQuery(GET_GAME_HISTORY, null);
        Log.d(TAG, "Loading game history: found " + cursor.getCount() + " games");
        if (!cursor.moveToFirst()) {
            return null;
        }
        ArrayList<Game> history = new ArrayList<>();
        while (!cursor.isAfterLast()) {
            history.add(getGameById(cursor.getLong(0)));
            cursor.moveToNext();
        }
        cursor.close();
        return history;
    }

    public Game getAndRemoveActiveGame() {
        Game game = getActiveGame();
        deleteActiveGame();
        return game;
    }

    private Game getGameFromCursors(Cursor gameCursor, Cursor turnsCursor) {
        if (!gameCursor.moveToFirst()) {
            return null;
        }
        turnsCursor.moveToFirst(); //no need to check it since game may have 0 turns
        Player cross = null, zero = null;
        User userCross = getUserById(gameCursor.getLong(1));
        int crossType = gameCursor.getInt(2);
        User userZero  = getUserById(gameCursor.getLong(3));
        int zeroType = gameCursor.getInt(4);
        long id = gameCursor.getLong(0);
        try {
            cross = (Player) playerClasses[crossType].getMethod("deserialize", User.class, GameLogic.PlayerFigure.class, Context.class).
                    invoke(null, userCross, GameLogic.PlayerFigure.CROSS, context);
            zero = (Player) playerClasses[zeroType].getMethod("deserialize", User.class, GameLogic.PlayerFigure.class, Context.class).
                    invoke(null, userZero, GameLogic.PlayerFigure.ZERO, context);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<GameEvent> turns = new ArrayList<>();
        while (!turnsCursor.isAfterLast()) {
            turns.add(GameEvent.deserialize(turnsCursor.getInt(0), turnsCursor.getInt(1), turnsCursor.getInt(2), turns.size()));
            turnsCursor.moveToNext();
        }

        return Game.deserializeGame(id, cross, crossType, zero, zeroType, GameLogic.deserialize(turns));
    }

    public boolean hasActiveGame() {
        Cursor cursor = getReadableDatabase().rawQuery(ACTIVE_GAME_COUNT, null);
        cursor.moveToFirst();
        int result = cursor.getInt(0);
        cursor.close();
        return result > 0;
    }

    public Game getGameById(long id) {
        String[] queryArgs = new String[] {Long.toString(id)};
        Cursor gameCursor = getReadableDatabase().rawQuery(GET_GAME_BY_ID, queryArgs);
        Cursor turnsCursor = getReadableDatabase().rawQuery(GET_TURNS_BY_GAME_ID, queryArgs);
        Log.d(TAG, "Loading game by id: found " + gameCursor.getCount() + " games and " + turnsCursor.getCount() + " turns");
        Game game = getGameFromCursors(gameCursor, turnsCursor);
        gameCursor.close();
        turnsCursor.close();
        return game;
    }

    public void addUser(User user, SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        cv.put(ID, user.getId());
        cv.put(GOOGLE_TOKEN, user.getGoogleToken());
        cv.put(NICKNAME_STR, user.getNickNameStr());
        cv.put(NICKNAME_ID, user.getNickNameId());
        cv.put(COLOR_CROSS, user.getColorCross());
        cv.put(COLOR_ZERO, user.getColorZero());
        db.replace(USER_TABLE, null, cv);
    }
    @Override
    public void addUser(User user) {
        addUser(user, getWritableDatabase());
    }

    @Override
    public User getUserById(long id) {
        String[] queryArgs = new String[] {Long.toString(id)};
        Cursor userCursor = getReadableDatabase().rawQuery(GET_USER_BY_ID, queryArgs);
        Log.d(TAG, "Loading user by id: found " + userCursor.getCount() + " users.");

        if (!userCursor.moveToFirst()) {
            Log.d(TAG, "No user loaded!");
            return null;
        }
        User user = new User(
                userCursor.getLong(0),
                userCursor.getString(1),
                userCursor.getString(2),
                userCursor.getInt(3),
                userCursor.getInt(4),
                userCursor.getInt(5),
                null /*FIXME Load separetly*/
        );

        userCursor.close();

        return user;
    }

    public void deleteGameById(long gameID) {
        Object[] params = new Object[]{gameID};
        getWritableDatabase().execSQL(DELETE_GAME_TURNS_BY_ID, params);
        getWritableDatabase().execSQL(DELETE_GAME_BY_ID, params);
    }
}
