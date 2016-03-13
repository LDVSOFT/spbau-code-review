package net.ldvsoft.warofviruses;

import android.util.Log;

import com.google.android.gms.iid.InstanceIDListenerService;

/**
 * Created by ldvsoft on 21.10.15.
 */
public class WoVInstanceIDListenerService extends InstanceIDListenerService {
    @Override
    public void onTokenRefresh() {
        Log.wtf(this.getClass().getName(), "OH YEAH TOKEN UPDATED.");
    }
}
