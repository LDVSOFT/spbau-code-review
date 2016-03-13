package net.ldvsoft.warofviruses;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by ldvsoft on 24.11.15.
 */
public class GCMHandler extends SmackCcsClient {
    protected Logger logger = Logger.getLogger(GCMHandler.class.getName());
    protected WarOfVirusesServer server;

    public GCMHandler(WarOfVirusesServer server) throws XMPPException, IOException, SmackException {
        this.server = server;

        String projectId = server.getSetting("google.projectId");
        String apiKey = server.getSetting("google.apiKey");
        String gcmServer = server.getSetting("gcm.server");
        int gcmPort = Integer.parseInt(server.getSetting("gcm.port"));
        connect(projectId, apiKey, gcmServer, gcmPort);
    }

    public String createJsonMessage(
            String to,
            String messageId,
            String action,
            JsonElement data,
            String collapseKey,
            Long timeToLive,
            Boolean delayWhileIdle,
            String priority) {
        JsonObject payload = new JsonObject();
        payload.addProperty(WoVProtocol.ACTION, action);
        // SIC: GCM does not accept tree-ish json
        payload.addProperty(WoVProtocol.DATA, data.toString());
        return createJsonMessage(to, messageId, payload, collapseKey, timeToLive, delayWhileIdle, priority);
    }

    @Override
    public boolean sendDownstreamMessage(String jsonRequest) {
        try {
            return super.sendDownstreamMessage(jsonRequest);
        } catch (SmackException.NotConnectedException e) {
            logger.log(Level.WARNING, "Why connection is closed?", e);
            return false;
        }
    }

    @Override
    protected void handleUpstreamMessage(JsonObject message) {
        super.handleUpstreamMessage(message);
        JsonObject answer = server.processGCM(message);
        if (answer != null) {
            sendDownstreamMessage(createJsonMessage(
                    message.get("from").getAsString(),
                    nextMessageId(),
                    WoVProtocol.RESULT,
                    answer,
                    null,
                    null,
                    false,
                    "high"
            ));
        }
    }

    @Override
    protected void handleNackReceipt(JsonObject JsonObject) {
        super.handleNackReceipt(JsonObject);
        String from = JsonObject.get("from").getAsString();
        String error = JsonObject.get("error").getAsString();
        if (error.equals("DEVICE_UNREGISTERED")) {
            server.getDatabaseHandler().deleteDeviceToken(from);
        }
    }

    public void stop() {
        disconnect();
    }
}
