package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by brendan on 29/01/2018.
 */

public class ScreenCaptureService extends Service
{

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // do your jobs here
        return super.onStartCommand(intent, flags, startId);
    }
}
