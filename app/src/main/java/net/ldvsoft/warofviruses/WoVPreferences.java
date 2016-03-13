package net.ldvsoft.warofviruses;

/**
 * Created by ldvsoft on 21.10.15.
 */
public abstract class WoVPreferences {
    public static final String GCM_SENT_TOKEN_TO_SERVER = "GCM_SENT_TOKEN_TO_SERVER";
    public static final String GCM_REGISTRATION_COMPLETE = "GCM_REGISTRATION_COMPLETE";
    public static final String REPLAY_GAME_ID = "replayGameId";
    public static final String REPLAY_GAME_TURN = "replayGameTurn";
    public static final String TURN_BROADCAST = "turnBroadcast";
    public static final String MAIN_BROADCAST = "mainBroadcast";
    public static final String BUNDLE = "bundle";
    public static final String GAME_JSON_DATA = "gameJsonData";

    public static final int OPPONENT_BOT = 0;
    public static final int OPPONENT_LOCAL_PLAYER = 1;
    public static final int OPPONENT_NETWORK_PLAYER = 2;
    public static final int OPPONENT_RESTORED_GAME = 3;

    public static final String OPPONENT_TYPE = "OPPONENT_TYPE";
    public static final String CURRENT_USER_ID = "currentUserID";
    public static final String PREFERENCES = "Preferences";

    public static final int DEFAULT_ZERO_COLOR = 210;
    public static final int DEFAULT_CROSS_COLOR = 30;

    public static final String AI_NICKNAME_STR = "SkyNet";
    public static final int AI_NICKNAME_ID = 1;
    public static final String AI_GOOGLE_TOKEN = "uniqueGoogleTokenForAiPlayer";

    public static final String ANONYMOUS_GOOGLE_TOKEN = "uniqueGoogleTokenForAnonymousPlayer";
    public static final String ANONYMOUS_NICKNAME_STR = "Anonymous";
    public static final int ANONYMOUS_NICKNAME_ID = 1;
}
