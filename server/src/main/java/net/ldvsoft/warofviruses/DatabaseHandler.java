package net.ldvsoft.warofviruses;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ldvsoft on 09.12.15.
 */
public class DatabaseHandler implements DBProvider {
    protected MysqlDataSource dataSource;
    protected WarOfVirusesServer server;

    private final static String DEVICE_USER = "user";
    private final static String GET_USER_BY_DEVICE_TOKEN = "SELECT * FROM " + USER_TABLE + " u INNER JOIN " + DEVICE_TABLE +
            " d ON d." + DEVICE_USER + " = u." + ID + " WHERE d." + TOKEN + " = ?;";
    private final static String GET_USER_BY_GOOGLE_TOKEN = "SELECT * FROM " + USER_TABLE + " WHERE "
            + GOOGLE_TOKEN + " = ?;";

    private Logger logger = Logger.getLogger(DatabaseHandler.class.getName());

    public DatabaseHandler(WarOfVirusesServer server) throws SQLException {
        this.server = server;

        dataSource = new MysqlDataSource();
        dataSource.setServerName(server.getSetting("db.server"));
        dataSource.setDatabaseName(server.getSetting("db.name"));
        dataSource.setUser(server.getSetting("db.user"));
        dataSource.setPassword(server.getSetting("db.password"));
    }

    public void stop() {
    }

    @Override
    public long addGame(Game game) {
        try {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                long id = game.getGameId();
                {
                    GameStatus status;
                    switch (game.getGameState()) {
                        case RUNNING:
                            status = GameStatus.RUNNING;
                            break;
                        case DRAW:
                            status = GameStatus.FINISHED_DRAW;
                            break;
                        case CROSS_WON:
                            status = GameStatus.FINISHED_CROSS_WON;
                            break;
                        case ZERO_WON:
                            status = GameStatus.FINISHED_ZERO_WON;
                            break;
                        default:
                            status = GameStatus.DELETED;
                            break;
                    }

                    PreparedStatement addGameStatement = connection.prepareStatement(ADD_GAME);
                    addGameStatement.setLong(1, id);
                    addGameStatement.setLong(2, game.getCrossPlayer().getUser().getId());
                    addGameStatement.setInt(3, game.getCrossType());
                    addGameStatement.setLong(4, game.getZeroPlayer().getUser().getId());
                    addGameStatement.setInt(5, game.getZeroType());
                    addGameStatement.setInt(6, status.ordinal());

                    addGameStatement.execute();
                }
                {
                    List<GameEvent> events = game.getGameLogic().getEventHistory();

                    PreparedStatement addGameTurnStatement = connection.prepareStatement(ADD_GAME_TURNS);
                    addGameTurnStatement.setLong(1, id);
                    for (int i = 0; i != events.size(); i++) {
                        addGameTurnStatement.setInt(2, i);

                        GameEvent event = events.get(i);
                        addGameTurnStatement.setInt(3, event.getEventTypeAsInt());
                        addGameTurnStatement.setInt(4, event.getTurnX());
                        addGameTurnStatement.setInt(5, event.getTurnY());

                        addGameTurnStatement.execute();
                    }
                }
                connection.commit();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to save game.", e);
        }
        return -1; //todo: return game id
    }

    @Override
    public void deleteActiveGame() {
        throw new UnsupportedOperationException("deleteActiveGame");
    }

    @Override
    public ArrayList<Game> getGameHistory() {
        throw new UnsupportedOperationException("getGameHistory");
    }

    @Override
    public Game getGameById(long id) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement getGameStatement = connection.prepareStatement(GET_GAME_BY_ID);
            getGameStatement.setLong(1, id);
            ResultSet game = getGameStatement.executeQuery();
            if (!game.first())
                return null;
            //TODO
            //TODO also do not forget about players types, blah, blah, blah
            Player cross = null, zero = null;

            PreparedStatement getGameTurnsStatement = connection.prepareStatement(GET_TURNS_BY_GAME_ID);
            getGameTurnsStatement.setLong(1, id);
            ResultSet events = getGameTurnsStatement.executeQuery();
            events.beforeFirst();
            ArrayList<GameEvent> eventList = new ArrayList<>();
            while (!events.isAfterLast()) {
                eventList.add(GameEvent.deserialize(events.getInt(1), events.getInt(2), events.getInt(3), eventList.size()));
                events.next();
            }

            return null;//Game.deserializeGame(id, cross, zero, GameLogic.deserialize(eventList));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load game.", e);
            return null;
        }
    }

    @Override
    public void addUser(User user) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement addUserStatement = connection.prepareStatement(ADD_USER);
            addUserStatement.setLong(1, user.getId());
            addUserStatement.setString(2, user.getGoogleToken());
            addUserStatement.setString(3, user.getNickNameStr());
            addUserStatement.setInt(4, user.getNickNameId());
            addUserStatement.setInt(5, user.getColorCross());
            addUserStatement.setInt(6, user.getColorZero());
            addUserStatement.setNull(7, Types.INTEGER);
            addUserStatement.execute();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Cannot find user", e);
        }
    }

    @Override
    public User getUserById(long id) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement getUserStatement = connection.prepareStatement(GET_USER_BY_ID);
            getUserStatement.setLong(1, id);
            ResultSet users = getUserStatement.executeQuery();
            if (!users.first())
                return null;

            return new User(
                    users.getLong(1),
                    users.getString(2),
                    users.getString(3),
                    users.getInt(4),
                    users.getInt(5),
                    users.getInt(6),
                    null /*FIXME Load separetly*/
            );
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Cannot find user", e);
            return null;
        }
    }

    private User getUserWithQuarry(String quarry, String param) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement getUserStatement = connection.prepareStatement(quarry);
            getUserStatement.setString(1, param);
            ResultSet users = getUserStatement.executeQuery();
            if (!users.first())
                return null;

            return new User(
                    users.getLong(1),
                    users.getString(2),
                    users.getString(3),
                    users.getInt(4),
                    users.getInt(5),
                    users.getInt(6),
                    null /*FIXME Load separetly*/
            );
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Cannot find user", e);
            return null;
        }
    }

    public User getUserByDeviceToken(String token) {
        return getUserWithQuarry(GET_USER_BY_DEVICE_TOKEN, token);
    }

    public User getUserByGoogleToken(String token) {
        return getUserWithQuarry(GET_USER_BY_GOOGLE_TOKEN, token);
    }

    public List<String> getDeviceTokens(long userId) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement getUserStatement = connection.prepareStatement(
                    "SELECT token FROM Device WHERE user = ?"
            );
            getUserStatement.setLong(1, userId);
            ResultSet users = getUserStatement.executeQuery();
            if (!users.first())
                return new ArrayList<>();

            ArrayList<String> res = new ArrayList<>();
            while (!users.isAfterLast()) {
                res.add(users.getString(1));
                users.next();
            }
            return res;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Cannot find user", e);
            return null;
        }
    }

    public void addDeviceToken(long userId, String deviceToken) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement addTokenStatement = connection.prepareStatement(ADD_DEVICE_TOKEN);
            addTokenStatement.setString(1, deviceToken);
            addTokenStatement.setLong(2, userId);

            addTokenStatement.execute();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Cannot add token:", e);
        }
    }


    public void deleteDeviceToken(String deviceToken) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement deleteTokenStatement = connection.prepareStatement(
                    "DELETE FROM " + DEVICE_TABLE + " WHERE " + TOKEN + " = ?;"
            );
            deleteTokenStatement.setString(1, deviceToken);

            deleteTokenStatement.execute();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Cannot add token:", e);
        }
    }
}
