package net.ldvsoft.warofviruses;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Created by Сева on 13.12.2015.
 */
public class ClientNetworkPlayer extends Player {
    private Context context;
    private BroadcastReceiver turnMessageReceiver = null;

    public static ClientNetworkPlayer deserialize(User user, GameLogic.PlayerFigure ownFigure, Context context) {
        return new ClientNetworkPlayer(user, ownFigure, context);
    }

    public ClientNetworkPlayer(User user, final GameLogic.PlayerFigure ownFigure, Context context) {
        this.user = user;
        this.ownFigure = ownFigure;
        this.context = context;
        this.type = 0;
        turnMessageReceiver = new BroadcastReceiver() {
            @Override
            public synchronized void onReceive(Context context, Intent intent) {
                String data = intent.getBundleExtra(WoVPreferences.BUNDLE).getString(WoVProtocol.DATA);
                if (data == null) {
                    throw new IllegalArgumentException("Missing data field in TURN_BUNDLE!");
                }
                JsonObject jsonData = (JsonObject) new JsonParser().parse(data);
                List<GameEvent> events = WoVProtocol.eventsFromJson(jsonData);
                game.applyPlayerEvents(events, ClientNetworkPlayer.this);
            }
        };
        context.registerReceiver(turnMessageReceiver, new IntentFilter(WoVPreferences.TURN_BROADCAST));
    }

    @Override
    public void makeTurn() {
        JsonObject myTurns = WoVProtocol.eventsToJson(game.getGameLogic()
                .getLastEventsBy(GameLogic.getOpponentPlayerFigure(ownFigure)));
        WoVGcmListenerService.sendGcmMessage(context, WoVProtocol.ACTION_TURN, myTurns);
    }

    @Override
    public void onGameStateChanged(GameEvent event, Player whoChanged) {
        //nothing to do
    }

    @Override
    public void onStop() {
        super.onStop();
        if (turnMessageReceiver != null) {
            context.unregisterReceiver(turnMessageReceiver);
            turnMessageReceiver = null;
        }
    }

    @Override
    public void updateGameInfo(Game game) {
        WoVGcmListenerService.sendGcmMessage(context, WoVProtocol.ACTION_UPDATE_LOCAL_GAME, new JsonObject());
    }
}
