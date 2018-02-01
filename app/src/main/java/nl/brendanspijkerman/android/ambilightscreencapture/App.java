package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import static android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS;

/**
 * Created by brendan on 29/01/2018.
 */

public class App extends Application
{


    @Override
    public void onCreate() {
        super.onCreate();
    }

}
