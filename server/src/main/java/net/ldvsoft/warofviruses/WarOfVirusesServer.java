package net.ldvsoft.warofviruses;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static net.ldvsoft.warofviruses.WoVProtocol.*;

public final class WarOfVirusesServer {
    private static final JsonObject JSON_RESULT_FAILURE = new JsonObject();

    static {
        JSON_RESULT_FAILURE.addProperty(RESULT, RESULT_FAILURE);
    }

    private static final String DEFAULT_CONFIG_FILE = "/etc/war-of-viruses-server.conf";

    private GoogleIdTokenVerifier verifier;
    private Random random = new SecureRandom();
    private Gson gson = new Gson();

    private Logger logger;
    private Properties config = new Properties();

    private GCMHandler gcmHandler;
    private DatabaseHandler databaseHandler;

    private User waitingForGame = null;
    private Game runningGame = null;

    public String getSetting(String name) {
        return config.getProperty(name, "");
    }

    public GCMHandler getGcmHandler() {
        return gcmHandler;
    }

    public DatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }

    /**
     * Process incoming via GCM message from client.
     * If simple answer is required, returns an answer.
     * May return null, but then client will get generic answer.
     * @param message Message from the client.
     * @return (Optional) Answer to client.
     */
    public JsonObject processGCM(JsonObject message) {
        String action = message.get("data").getAsJsonObject().get(ACTION).getAsString();
        switch (action) {
            case ACTION_PING:
                return processPing(message);
            case ACTION_USER_READY:
                return processUserReady(message);
            case ACTION_TURN:
                return processTurn(message);
            case ACTION_UPDATE_LOCAL_GAME:
                return processUpdateLocalGame(message);
            case ACTION_LOGIN:
                return processLogin(message);
            case ACTION_LOGOUT:
                return processLogout(message);
            default:
                return null;
        }
    }

    private JsonObject processLogin(JsonObject message) {
        boolean isGood = true;

        String deviceToken = message.get("from").getAsString();
        JsonObject data = (JsonObject) new JsonParser().parse(
                message.get("data").getAsJsonObject().get("data").getAsString()
        );
        User user = null;
        User localUser = gson.fromJson(data.getAsJsonObject(LOCAL_USER), User.class);
        String googleToken = null;
        boolean isRefreshment = !data.has(GOOGLE_TOKEN);
        if (!isRefreshment) {
            String encodedGoogleToken = data.get(GOOGLE_TOKEN).getAsString();
            GoogleIdToken googleIdToken;

            try {
                googleIdToken = verifier.verify(encodedGoogleToken);
            } catch (GeneralSecurityException | IOException e) {
                logger.log(Level.SEVERE, "What's wrong with that google verification?!", e);
                return JSON_RESULT_FAILURE;
            }

            if (googleIdToken == null) {
                logger.log(Level.WARNING, "Wrong google token!");
                isGood = false;
            } else {
                GoogleIdToken.Payload payload = googleIdToken.getPayload();
                googleToken = payload.getSubject();
            }

            if (isGood && googleToken == null) {
                logger.log(Level.WARNING, "Wrong google token: no subject!");
                isGood = false;
            }
            user = databaseHandler.getUserByGoogleToken(googleToken);
        } else {
            user = databaseHandler.getUserByGoogleToken(localUser.getGoogleToken());
            isGood = user != null;
        }
        if (isGood) {
            // Now user is OK, now we need to find it in DB or create new
            if (user == null) {
                // Create new one!
                user = new User(
                        Math.abs(random.nextLong()), googleToken,
                        localUser.getNickNameStr(), localUser.getNickNameId(),
                        localUser.getColorCross(), localUser.getColorZero(),
                        null);
                databaseHandler.addUser(user);
            } else if (isRefreshment) {
                // Upgrade old one
                user.setCrossColor(localUser.getColorCross());
                user.setZeroColor(localUser.getColorZero());
                user.setNickNameStr(localUser.getNickNameStr());
                databaseHandler.addUser(user);
            }
            databaseHandler.addDeviceToken(user.getId(), deviceToken);
        }

        if (!isRefreshment) {
            JsonObject answer = new JsonObject();
            answer.addProperty(RESULT, isGood ? RESULT_SUCCESS : RESULT_FAILURE);
            if (isGood) {
                answer.add(USER, gson.toJsonTree(user));
            }
            gcmHandler.sendDownstreamMessage(gcmHandler.createJsonMessage(
                    deviceToken,
                    gcmHandler.nextMessageId(),
                    ACTION_LOGIN_COMPLETE,
                    answer,
                    null,
                    null,
                    false,
                    "high"
            ));
        }
        return null;
    }

    private JsonObject processLogout(JsonObject message) {
        databaseHandler.deleteDeviceToken(message.get("from").getAsString());
        return null;
    }

    private JsonObject processUpdateLocalGame(JsonObject message) {
        User sender = databaseHandler.getUserByDeviceToken(message.get("from").getAsString());
        if (sender.getId() == runningGame.getCrossPlayer().getUser().getId()) {
            ((ServerNetworkPlayer) runningGame.getCrossPlayer()).sendGameInfo(); //how about more elegant solution?
        } else {
            ((ServerNetworkPlayer) runningGame.getZeroPlayer()).sendGameInfo();
        }
        return null;
    }

    private JsonObject processPing(JsonObject message) {
        String sender = message.get("from").getAsString();
        String messageId = message.get("message_id").getAsString();

        JsonObject answer = new JsonObject();
        answer.addProperty(RESULT, RESULT_SUCCESS);
        answer.addProperty(PING_ID, messageId);

        gcmHandler.sendDownstreamMessage(gcmHandler.createJsonMessage(
                        sender,
                        gcmHandler.nextMessageId(),
                        RESULT_SUCCESS,
                        answer,
                        null,
                        null,
                        false,
                        "high")
        );
        return null;
    }

    private JsonObject processUserReady(JsonObject message) {
        User sender = databaseHandler.getUserByDeviceToken(message.get("from").getAsString());
        if (waitingForGame == null) {
            logger.log(Level.INFO, "User " + sender.getId() + " started waiting.");
            waitingForGame = sender;
        } else {
            logger.log(Level.INFO, "User " + sender.getId() + " came to start the game.");
            runningGame = new Game();
            runningGame.startNewGame(
                    new ServerNetworkPlayer(sender, waitingForGame, this, GameLogic.PlayerFigure.CROSS),
                    new ServerNetworkPlayer(waitingForGame, sender, this, GameLogic.PlayerFigure.ZERO));
            waitingForGame = null;
        }
        return null;
    }

    private JsonObject processTurn(JsonObject message) {
        User sender = databaseHandler.getUserByDeviceToken(message.get("from").getAsString());
        JsonObject data = (JsonObject) new JsonParser().parse(
                message.get("data").getAsJsonObject().get("data").getAsString()
        );
        if (runningGame.getCrossPlayer().getUser().getId() == sender.getId()) {
            ((ServerNetworkPlayer) runningGame.getCrossPlayer()).performMove(data);
        } else {
            ((ServerNetworkPlayer) runningGame.getZeroPlayer()).performMove(data);
        }
        return null;
    }

    /**
     * Sends message to user
     * @param user target user id
     * @param action action specifier
     * @param data additional JSON data, specifying action params
     */
    public boolean sendToUser(User user, String action, JsonObject data) {
        List<String> tokens = databaseHandler.getDeviceTokens(user.getId());
        boolean success = true;
        for (String token : tokens) {
            success = success && gcmHandler.sendDownstreamMessage(gcmHandler.createJsonMessage(
                    token, gcmHandler.nextMessageId(), action, data, null, null, false, "high"
            ));
        }
        return success;
    }

    public void run() {
        String configFile = System.getenv("CONFIG");
        if (configFile == null) {
            configFile = DEFAULT_CONFIG_FILE;
        }

        logger = Logger.getLogger(WarOfVirusesServer.class.getName());
        try {
            LogManager.getLogManager().readConfiguration(new FileInputStream(configFile));
            config.load(new FileReader(configFile));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot open config file or it's contents are wrong.", e);
            System.exit(1);
        }
        logger = Logger.getLogger(WarOfVirusesServer.class.getName());

        try {
            databaseHandler = new DatabaseHandler(this);
            gcmHandler = new GCMHandler(this);
            verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(getSetting("google.serverClientId")))
                    .setIssuer("https://accounts.google.com")
                    .build();

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    gcmHandler.stop();
                    databaseHandler.stop();
                }
            }));

            while (!Thread.interrupted()) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Server failed", e);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        WarOfVirusesServer instance = new WarOfVirusesServer();
        instance.run();
    }
}
