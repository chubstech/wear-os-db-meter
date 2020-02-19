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

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.wear.ambient.AmbientModeSupport;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MainActivity extends FragmentActivity {
    static final private double EMA_FILTER = 0.6;
    private static double mEMA = 0.0;
    TextView mStatusView, mStatusAvgView;
    MediaRecorder mRecorder;

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


        /*
         * Create a PendingIntent which we'll give to the AlarmManager to send ambient mode updates
         * on an interval which we've define.
         */

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


        setContentView(R.layout.activity_main);
        mStatusView = findViewById(R.id.dbText);
        mStatusAvgView = findViewById(R.id.time);
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void onResume() {
        super.onResume();
        startRecorder();


        IntentFilter filter = new IntentFilter(AMBIENT_UPDATE_ACTION);

        refreshDisplayAndSetNextUpdate();
    }

    public void onPause() {
        super.onPause();
        stopRecorder();


        mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);
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
    ArrayList<Observation> obs = new ArrayList<>();

    String user_id = "BBNN21";

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

        /* Calculate next trigger time (based on state). */
        long delayMs = ACTIVE_INTERVAL_MS - (timeMs % ACTIVE_INTERVAL_MS) + extraTime;
        mActiveModeUpdateHandler.removeMessages(MSG_UPDATE_SCREEN);
        mActiveModeUpdateHandler.sendEmptyMessageDelayed(MSG_UPDATE_SCREEN, delayMs);

        double amplitude = mRecorder.getMaxAmplitude();
        if(amplitude > 0 && amplitude < 1000000) {

            double dbl = convertdDb(amplitude);
            if(Math.abs(dbl - lastReading) < 2) return;
            lastReading = dbl;
            long reading = Math.round(dbl);
            long ut1 = Instant.now().getEpochSecond();
            Observation newObs = new Observation(ut1, (int) reading);
            obs.add(newObs);
            System.out.println(obs.size());
            if(obs.size() > 256) {
                ArrayList<Observation> tmpObs = obs;
                obs = new ArrayList<>();
                sendPost(tmpObs);
            }
            mStatusView.setText(reading + "");

        }
    }

    JSONObject getObs(Observation toSend){
        JSONObject obs = new JSONObject();
        try {
            obs.put("time_obs", toSend.getTimeObs());
            obs.put("db_reading", toSend.getDbReading());
        } catch(Exception e) {

        }
        return obs;
    }

    public JSONArray bundleObs(ArrayList<Observation> dataToSend){

        JSONArray observations = new JSONArray();
        for(Observation obs : dataToSend) {
            observations.put(getObs(obs));
        }

        return observations;
    }

    public void sendPost(final ArrayList<Observation> dataToSend) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://noise-wearable.herokuapp.com/api/noise_observation");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("user_id", user_id);
                    jsonParam.put("data", bundleObs(dataToSend));

                    Log.i("JSON", jsonParam.toString());
                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    //os.writeBytes(URLEncoder.encode(jsonParam.toString(), "UTF-8"));
                    os.writeBytes(jsonParam.toString());

                    os.flush();
                    os.close();

                    Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                    Log.i("MSG" , conn.getResponseMessage());

                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    /** Updates display based on Ambient state. If you need to pull data, you should do it here. */
    private void loadDataAndUpdateScreen() {

    }

    private class Observation {
        private long time_obs;
        private int db_reading;

        public Observation(long time_obs, int db_reading) {
            this.time_obs = time_obs;
            this.db_reading = db_reading;
        }

        public long getTimeObs() {
            return time_obs;
        }

        public int getDbReading() {
            return db_reading;
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
