package net.ldvsoft.warofviruses;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ldvsoft on 23.11.15.
 */
public class WoVProtocol {
    public static final String RESULT = "result";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";

    public static final String ACTION = "action";
    public static final String ACTION_PING = "ping";

    public static final String PING_ID = "id";
    public static final String ACTION_TURN = "turn";

    public static final String GAME_LOADED = "gameLoaded";
    public static final String TURN_ARRAY = "turnArray";
    public static final String GAME_ID = "gameID";
    public static final String DATA = "data";
    public static final String CROSS_USER = "crossUser";
    public static final String ZERO_USER = "zeroUser";
    public static final String MY_FIGURE = "myFigure";
    public static final String ACTION_USER_READY = "userReady";
    public static final String ACTION_UPDATE_LOCAL_GAME = "updateLocalGame";

    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_LOGOUT = "logout";
    public static final String GOOGLE_TOKEN = "googleToken";
    public static final String LOCAL_USER = "localUser";

    public static final String ACTION_LOGIN_COMPLETE = "loginComplete";
    public static final String USER = "user";

    private static final Gson gson = new Gson();
    public static List<GameEvent> eventsFromJson(JsonObject jsonData) {
        int[] turnArray = gson.fromJson(jsonData.get(WoVProtocol.TURN_ARRAY), int[].class);
        List<GameEvent> events = new ArrayList<>();
        for (int i = 0; i < turnArray.length; i += 3) {
            events.add(GameEvent.deserialize(turnArray[i], turnArray[i + 1], turnArray[i + 2], i));
        }
        return events;
    }

    public static JsonObject eventsToJson(List<GameEvent> events) {
        int[] result = new int[events.size() * 3];
        for (int i = 0; i < events.size(); i++) {
            GameEvent event = events.get(i);
            result[3 * i] = event.getEventTypeAsInt();
            result[3 * i + 1] = event.getTurnX();
            result[3 * i + 2] = event.getTurnY();
        }

        JsonObject data = new JsonObject();
        data.add(WoVProtocol.TURN_ARRAY, gson.toJsonTree(result));
        return data;
    }
}
