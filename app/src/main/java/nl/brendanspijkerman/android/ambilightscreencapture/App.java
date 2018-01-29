package nl.brendanspijkerman.android.ambilightscreencapture;

import android.app.Application;
import android.content.Intent;

/**
 * Created by brendan on 29/01/2018.
 */

public class App extends Application
{

    @Override
    public void onCreate() {
        super.onCreate();

        startService(new Intent(this, ScreenCaptureService.class));
    }

}
