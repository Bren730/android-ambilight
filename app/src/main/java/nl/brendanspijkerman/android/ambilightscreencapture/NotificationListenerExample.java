package nl.brendanspijkerman.android.ambilightscreencapture;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;

import java.util.List;

/**
 * Created by brendan on 30/01/2018.
 */

public class NotificationListenerExample extends NotificationListenerService implements MediaSessionManager.OnActiveSessionsChangedListener
{

    private static final String TAG = NotificationListenerExample.class.getSimpleName();
    private static final int REQUEST_MEDIA_CONTENT_CONTROL = 1;

    @Override
    public IBinder onBind(Intent intent) {
        // Do stuff here

        return super.onBind(intent);
    }

    @Override
    public void onCreate()
    {
        int permissionCheck = ContextCompat.checkSelfPermission(getBaseContext(),
                Manifest.permission.MEDIA_CONTENT_CONTROL);

        if (permissionCheck == PackageManager.PERMISSION_DENIED)
        {
            Log.e(TAG, "App doesn't have permission to control media");

//            ActivityCompat.requestPermissions(getBaseContext().geta,
//                    new String[]{Manifest.permission.MEDIA_CONTENT_CONTROL},
//                    REQUEST_MEDIA_CONTENT_CONTROL);
        }

        MediaSessionManager mediaSessionManager =
                (MediaSessionManager)
                        getApplicationContext().getSystemService(Context.MEDIA_SESSION_SERVICE);

        ComponentName componentName = new ComponentName(this, this.getClass());
        try
        {
            mediaSessionManager.addOnActiveSessionsChangedListener(this, componentName);

            // Trigger change event with existing set of sessions.
            List<MediaController> initialSessions = mediaSessionManager.getActiveSessions(componentName);
            onActiveSessionsChanged(initialSessions);
        }
        catch (Exception e)
        {
            Log.e(TAG, e.toString());
        }




    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        Log.i(TAG, "New notification posted");
        // Implement what you want here
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        // Implement what you want here
    }


    @Override
    public void onActiveSessionsChanged(List<MediaController> activeMediaControllers)
    {
        for (MediaController mediaController : activeMediaControllers)
        {
            MediaController.PlaybackInfo info = mediaController.getPlaybackInfo();
            MediaMetadata meta = mediaController.getMetadata();
            Bundle extras = mediaController.getExtras();
            long flags = mediaController.getFlags();
            String packageName = mediaController.getPackageName();
//            MediaFormat = mediaController.
            info.toString();
        }

        Log.d(TAG, "Active MediaSessions changed");
        Log.d(TAG, activeMediaControllers.toString());
    }

}
