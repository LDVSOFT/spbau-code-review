package net.ldvsoft.warofviruses;

import java.util.ArrayList;

/**
 * Created by Сева on 11.12.2015.
 */
public interface DBProvider {
    String GAME_TABLE = "Game";
    String ID = "id";
    String GAME_STATUS = "status";
    String GAME_DATE = "gameDate";
    String PLAYER_ZERO = "playerZero";
    String PLAYER_CROSSES = "playerCrosses";

    String DEVICE_TABLE = "Device";
    String TURN_TABLE = "Turn";
    String GAME_ID = "game";
    String TURN_NUMBER = "turnNo";
    String TURN_TYPE = "type";
    String TURN_X = "x";
    String TURN_Y = "y";
    String USER_TABLE = "User";
    String GOOGLE_TOKEN = "googleToken";
    String NICKNAME_STR = "nicknameStr";
    String NICKNAME_ID = "nicknameID";
    String COLOR_CROSS = "colorCross";
    String COLOR_ZERO = "colorZero";
    String INVITATION_TARGET = "invitationTarget";
    String PLAYER_CROSSES_TYPE = "playerCrossesType";
    String PLAYER_ZEROES_TYPE = "playerZeroesType";
    String TOKEN = "token";
    String USER = "user";

    /**
     * Special user ids.
     */
    int USER_AI_PLAYER_ID = 1;
    int USER_ANONYMOUS_ID = 0;

    enum GameStatus {RUNNING, DELETED, FINISHED_DRAW, FINISHED_ZERO_WON, FINISHED_CROSS_WON}

    ; //probably not there, in some other class

    String GET_ACTIVE_GAME = "SELECT * FROM " + GAME_TABLE +
            " WHERE " + GAME_STATUS + " = " + GameStatus.RUNNING.ordinal() + ";";

    String GET_ACTIVE_GAME_TURNS = "SELECT " + TURN_TYPE + ", " + TURN_X + ", " + TURN_Y +
            " FROM " + TURN_TABLE + " INNER JOIN " + GAME_TABLE + " ON " + GAME_TABLE + "." + ID + " = " + TURN_TABLE + "." + GAME_ID +
            " AND " + GAME_TABLE + "." + GAME_STATUS + " = " + GameStatus.RUNNING.ordinal() + " ORDER BY " + TURN_NUMBER + " ASC;";

    String DELETE_ACTIVE_GAME_TURNS = "DELETE FROM " + TURN_TABLE + " WHERE " + GAME_ID + " IN (" +
            " SELECT " + ID + " FROM " + GAME_TABLE + " WHERE " + ID + " = " + GAME_ID + " AND " + GAME_STATUS + " = " +
            GameStatus.RUNNING.ordinal() + ");";

    String DELETE_ACTIVE_GAME = "DELETE FROM " + GAME_TABLE + " WHERE " + GAME_STATUS +
            " =" + GameStatus.RUNNING.ordinal() + ";";

    String GET_GAME_HISTORY = "SELECT " + ID + " FROM " + GAME_TABLE + " WHERE " +
            GAME_STATUS + " >= 1 ORDER BY " + GAME_DATE + " DESC;";

    String GET_GAME_BY_ID = "SELECT * FROM " + GAME_TABLE + " WHERE " + ID + " = ?;";

    String GET_TURNS_BY_GAME_ID = "SELECT " + TURN_TYPE + ", " + TURN_X + ", " + TURN_Y +
            " FROM " + TURN_TABLE + " WHERE " + GAME_ID + " = ? ORDER BY " + TURN_NUMBER + " ASC;";

    String ADD_GAME = "INSERT INTO " + GAME_TABLE + "(" + ID + ", " + PLAYER_CROSSES + ", " + PLAYER_ZERO +
            ", " + GAME_STATUS + ", " + GAME_DATE + ") VALUES (?, ?, ?, ?, ?);";

    String ADD_GAME_TURNS = "INSERT INTO " + TURN_TABLE + "(" + GAME_ID + ", " + TURN_NUMBER + ", " + TURN_TYPE +
            ", " + TURN_X + ", " + TURN_Y + ") VALUES (?, ?, ?, ?, ?);";

    String GET_USER_BY_ID = "SELECT * FROM " + USER_TABLE + " WHERE " + ID + " = ?;";

    String ADD_USER = "INSERT INTO " + USER_TABLE + " VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
            ID + " = VALUES(" + ID + "), " + GOOGLE_TOKEN + " = VALUES(" + GOOGLE_TOKEN + "), " +
            NICKNAME_STR + " = VALUES(" + NICKNAME_STR + "), " + NICKNAME_ID + " = VALUES(" + NICKNAME_ID + "), " +
            COLOR_CROSS + " = VALUES(" + COLOR_CROSS + "), " + COLOR_ZERO + " = VALUES(" + COLOR_ZERO + "), " +
            INVITATION_TARGET + " = VALUES(" + INVITATION_TARGET + ");";

    String ADD_DEVICE_TOKEN = "INSERT INTO " + DEVICE_TABLE + " VALUES (?, ?) ON DUPLICATE KEY UPDATE " +
            TOKEN + " = VALUES(" + TOKEN + "), " + USER + " = VALUES(" + USER + ");";

    String ACTIVE_GAME_COUNT = "SELECT COUNT(*) FROM " + GAME_TABLE +
            " WHERE " + GAME_STATUS + " = " + GameStatus.RUNNING.ordinal() + ";";

    String DELETE_GAME_TURNS_BY_ID = "DELETE FROM " + TURN_TABLE + " WHERE " + GAME_ID + " = ?;";

    String DELETE_GAME_BY_ID = "DELETE FROM " + GAME_TABLE + " WHERE " + ID + " = ?;";

    //adds game to database and returns its ID
    long addGame(Game game);
    void deleteActiveGame();
    ArrayList<Game> getGameHistory();
    Game getGameById(long id);

    void addUser(User user);
    User getUserById(long id);
}
