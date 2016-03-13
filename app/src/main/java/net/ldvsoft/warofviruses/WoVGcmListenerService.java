package net.ldvsoft.warofviruses;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.UUID;

public class WoVGcmListenerService extends GcmListenerService {

    private static final String TAG = "WoVGcmListenerService";

    /**
     * Called when message is received.
     *
     * @param from SenderID of the sender.
     * @param data Data bundle containing message data as key/value pairs.
     *             For Set of keys use data.keySet().
     */
    @Override
    public void onMessageReceived(String from, Bundle data) {
        Log.i(TAG, "From: " + from);
        Log.i(TAG, "Message: " + data.toString());
        String action = (String) data.get(WoVProtocol.ACTION);
        Intent intent = new Intent();
        intent.putExtra(WoVPreferences.BUNDLE, data);
        switch (action) {
            case WoVProtocol.ACTION_TURN:
                intent.setAction(WoVPreferences.TURN_BROADCAST);
                break;
            case WoVProtocol.GAME_LOADED:
            case WoVProtocol.ACTION_LOGIN_COMPLETE:
            case WoVProtocol.ACTION_PING:
                intent.setAction(WoVPreferences.MAIN_BROADCAST);
                break;
        }
        sendBroadcast(intent);
    }

    public static void sendGcmMessage(final Context context, final String action, final JsonObject data) {
        //AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
        //    @Override
        //    protected Void doInBackground(Void... params) {
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
                String id = "m-" + UUID.randomUUID().toString();
                Bundle message = new Bundle();
                message.putString(WoVProtocol.ACTION, action);
                if (data != null) {
                    message.putString(WoVProtocol.DATA, data.toString());
                }
                try {
                    Log.d(TAG, "SENDING " + action + " TO " + context.getString(R.string.gcm_defaultSenderId) + " ID " + id);
                    gcm.send(context.getString(R.string.gcm_defaultSenderId) + "@gcm.googleapis.com", id, message);
                    Log.d(TAG, "OVER?");
                } catch (IOException e) {
                    Log.wtf(TAG, "Something really wrong:", e);
                }
        //        return null;
        //    }
        //};
        //task.execute();
    }

    @Override
    public void onMessageSent(String msgId) {
        super.onMessageSent(msgId);
        Log.d(TAG, "Message ID=" + msgId + " sent!");
    }

    @Override
    public void onSendError(String msgId, String error) {
        super.onSendError(msgId, error);
        Log.d(TAG, "Message ID=" + msgId + " not sent: " + error + "!");
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        Log.d(TAG, "Deleted?!");
    }
}