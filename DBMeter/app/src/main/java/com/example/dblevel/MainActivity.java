package com.example.dblevel;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends FragmentActivity implements AmbientModeSupport.AmbientCallbackProvider {
    static final private double EMA_FILTER = 0.6;
    private static double mEMA = 0.0;
    final Handler mHandler = new Handler();
    TextView mStatusView, mStatusAvgView;
    MediaRecorder mRecorder;
    private List<Double> valuesAvg = new ArrayList<>();
    private long timestamp = System.currentTimeMillis() / 100L;
    private long lastTimestamp = System.currentTimeMillis() / 100L;
    private ConstraintLayout constraintLayout;

    private static final String TAG = "MainActivity";

    /** Custom 'what' for Message sent to Handler. */
    private static final int MSG_UPDATE_SCREEN = 0;

    /** Milliseconds between updates based on state. */
    private static final long ACTIVE_INTERVAL_MS = 50;

    private static final long AMBIENT_INTERVAL_MS = 50;

    /** Action for updating the display in ambient mode, per our custom refresh cycle. */
    private static final String AMBIENT_UPDATE_ACTION = "com.example.android.wearable.wear.alwayson.action.AMBIENT_UPDATE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);

        mAmbientController = AmbientModeSupport.attach(this);

        mAmbientUpdateAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        /*
         * Create a PendingIntent which we'll give to the AlarmManager to send ambient mode updates
         * on an interval which we've define.
         */
        Intent ambientUpdateIntent = new Intent(AMBIENT_UPDATE_ACTION);

        /*
         * Retrieves a PendingIntent that will perform a broadcast. You could also use getActivity()
         * to retrieve a PendingIntent that will start a new activity, but be aware that actually
         * triggers onNewIntent() which causes lifecycle changes (onPause() and onResume()) which
         * might trigger code to be re-executed more often than you want.
         *
         * If you do end up using getActivity(), also make sure you have set activity launchMode to
         * singleInstance in the manifest.
         *
         * Otherwise, it is easy for the AlarmManager launch Intent to open a new activity
         * every time the Alarm is triggered rather than reusing this Activity.
         */
        mAmbientUpdatePendingIntent =
                PendingIntent.getBroadcast(
                        this, 0, ambientUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*
         * An anonymous broadcast receiver which will receive ambient update requests and trigger
         * display refresh.
         */
        mAmbientUpdateBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        refreshDisplayAndSetNextUpdate();
                    }
                };




        setContentView(R.layout.activity_main);
        constraintLayout = findViewById(R.id.background);
        mStatusView = findViewById(R.id.dbText);
        mStatusAvgView = findViewById(R.id.time);
    }

    public void onResume() {
        super.onResume();
        startRecorder();

        Log.d(TAG, "onResume()");

        IntentFilter filter = new IntentFilter(AMBIENT_UPDATE_ACTION);
        registerReceiver(mAmbientUpdateBroadcastReceiver, filter);

        refreshDisplayAndSetNextUpdate();
    }

    public void onPause() {
        super.onPause();
        stopRecorder();
        Log.d(TAG, "onPause()");

        unregisterReceiver(mAmbientUpdateBroadcastReceiver);

        mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);
        mAmbientUpdateAlarmManager.cancel(mAmbientUpdatePendingIntent);
    }

    public void startRecorder() {
        if (mRecorder == null) {
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mRecorder.setOutputFile("/dev/null");
            try {
                mRecorder.prepare();
            } catch (java.io.IOException ioe) {
                android.util.Log.e("[Monkey]", "IOException: " +
                        android.util.Log.getStackTraceString(ioe));

            } catch (java.lang.SecurityException e) {
                android.util.Log.e("[Monkey]", "SecurityException: " +
                        android.util.Log.getStackTraceString(e));
            }
            try {
                mRecorder.start();
            } catch (java.lang.SecurityException e) {
                android.util.Log.e("[Monkey]", "SecurityException: " +
                        android.util.Log.getStackTraceString(e));
            }

            //mEMA = 0.0;
        }

    }

    public void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
    }

    public double soundDb(double ampl) {
        return 20 * (float) Math.log10(getAmplitudeEMA() / ampl);
    }
    public double convertdDb(double amplitude) {
        // Cellphones can catch up to 90 db + -
        // getMaxAmplitude returns a value between 0-32767 (in most phones). that means that if the maximum db is 90, the pressure
        // at the microphone is 0.6325 Pascal.
        // it does a comparison with the previous value of getMaxAmplitude.
        // we need to divide maxAmplitude with (32767/0.6325)
        //51805.5336 or if 100db so 46676.6381
        double EMA_FILTER = 0.6;
        SharedPreferences sp = this.getSharedPreferences("device-base", MODE_PRIVATE);
        double amp = (double) sp.getFloat("amplitude", 0);
        double mEMAValue = EMA_FILTER * amplitude + (1.0 - EMA_FILTER) * mEMA;
        Log.d("db", Double.toString(amp));
        //Assuming that the minimum reference pressure is 0.000085 Pascal (on most phones) is equal to 0 db
        // samsung S9 0.000028251
        return 20 * (float) Math.log10((mEMAValue/51805.5336)/ 0.000028251);
    }


    public double getAmplitude() {
        if (mRecorder != null)
            return (mRecorder.getMaxAmplitude());
        else
            return 0;

    }

    public double getAmplitudeEMA() {
        double amp = getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }

    public void pauseRecorder() {
        if (mRecorder != null) {
            mRecorder.pause();
        }
    }


    public void unpauseRecorder() {
        if (mRecorder != null) {
            mRecorder.start();
        }
    }









    private AmbientModeSupport.AmbientController mAmbientController;

    /** If the display is low-bit in ambient mode. i.e. it requires anti-aliased fonts. */
    boolean mIsLowBitAmbient;

    /**
     * If the display requires burn-in protection in ambient mode, rendered pixels need to be
     * intermittently offset to avoid screen burn-in.
     */
    boolean mDoBurnInProtection;

    private final SimpleDateFormat sDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private volatile int mDrawCount = 0;

    /**
     * Since the handler (used in active mode) can't wake up the processor when the device is in
     * ambient mode and undocked, we use an Alarm to cover ambient mode updates when we need them
     * more frequently than every minute. Remember, if getting updates once a minute in ambient mode
     * is enough, you can do away with the Alarm code and just rely on the onUpdateAmbient()
     * callback.
     */
    private AlarmManager mAmbientUpdateAlarmManager;

    private PendingIntent mAmbientUpdatePendingIntent;
    private BroadcastReceiver mAmbientUpdateBroadcastReceiver;

    /**
     * This custom handler is used for updates in "Active" mode. We use a separate static class to
     * help us avoid memory leaks.
     */
    private final Handler mActiveModeUpdateHandler = new ActiveModeUpdateHandler(this);

    /**
     * Loads data/updates screen (via method), but most importantly, sets up the next refresh
     * (active mode = Handler and ambient mode = Alarm).
     */
    private double lastReading = 0;
    private boolean vibrating = false;
    private long count = 0;
    private void refreshDisplayAndSetNextUpdate() {

        loadDataAndUpdateScreen();

        long timeMs = System.currentTimeMillis();
        long extraTime = 0;
        if(lastReading > 75 && !vibrating) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                extraTime = 1000;
                vibrating = true;
        } else {
            if(count++ == 8) {
                vibrating = false;
                count = 0;
            }
        }

        if (mAmbientController.isAmbient()) {
            /* Calculate next trigger time (based on state). */
            long delayMs = AMBIENT_INTERVAL_MS - (timeMs % AMBIENT_INTERVAL_MS)  + extraTime;
            long triggerTimeMs = timeMs + delayMs;

            mAmbientUpdateAlarmManager.setExact(
                    AlarmManager.RTC_WAKEUP, triggerTimeMs, mAmbientUpdatePendingIntent);
        } else {
            /* Calculate next trigger time (based on state). */
            long delayMs = ACTIVE_INTERVAL_MS - (timeMs % ACTIVE_INTERVAL_MS) + extraTime;

            mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);
            mActiveModeUpdateHandler.sendEmptyMessageDelayed(MSG_UPDATE_SCREEN, delayMs);
        }

        double amplitude = mRecorder.getMaxAmplitude();
        if(amplitude > 0 && amplitude < 1000000) {

            double dbl = convertdDb(amplitude);
            if(Math.abs(dbl - lastReading) < 2) return;
            lastReading = dbl;
            long reading = Math.round(dbl);
            mStatusView.setText(reading + "");

        }
    }

    /** Updates display based on Ambient state. If you need to pull data, you should do it here. */
    private void loadDataAndUpdateScreen() {

    }

    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new MyAmbientCallback();
    }

    private class MyAmbientCallback extends AmbientModeSupport.AmbientCallback {
        /** Prepares the UI for ambient mode. */
        @Override
        public void onEnterAmbient(Bundle ambientDetails) {
            super.onEnterAmbient(ambientDetails);

            refreshDisplayAndSetNextUpdate();
        }

        /**
         * Updates the display in ambient mode on the standard interval. Since we're using a custom
         * refresh cycle, this method does NOT update the data in the display. Rather, this method
         * simply updates the positioning of the data in the screen to avoid burn-in, if the display
         * requires it.
         */
        @Override
        public void onUpdateAmbient() {
            super.onUpdateAmbient();

        }

        /** Restores the UI to active (non-ambient) mode. */
        @Override
        public void onExitAmbient() {
            super.onExitAmbient();

            /* Clears out Alarms since they are only used in ambient mode. */
            mAmbientUpdateAlarmManager.cancel(mAmbientUpdatePendingIntent);

            refreshDisplayAndSetNextUpdate();
        }
    }

    /** Handler separated into static class to avoid memory leaks. */
    private static class ActiveModeUpdateHandler extends Handler {
        private final WeakReference<MainActivity> mMainActivityWeakReference;

        ActiveModeUpdateHandler(MainActivity reference) {
            mMainActivityWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message message) {
            MainActivity mainActivity = mMainActivityWeakReference.get();

            if (mainActivity != null) {
                switch (message.what) {
                    case MSG_UPDATE_SCREEN:
                        mainActivity.refreshDisplayAndSetNextUpdate();
                        break;
                }
            }
        }
    }
}
