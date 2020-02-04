package com.example.dblevel;

import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends WearableActivity {
    static final private double EMA_FILTER = 0.6;
    private static double mEMA = 0.0;
    final Handler mHandler = new Handler();
    TextView mStatusView, mStatusAvgView, mStatus;
    MediaRecorder mRecorder;
    final Runnable updater = new Runnable() {

        public void run() {
            updateTv();
        }

        ;
    };
    Thread runner;
    private List<Double> valuesAvg = new ArrayList<>();
    private long timestamp = System.currentTimeMillis() / 1000L;
    private long lastTimestamp = System.currentTimeMillis() / 1000L;
    private String schedule = "NA";

    Date c = Calendar.getInstance().getTime();
    SimpleDateFormat df   = new SimpleDateFormat("dd-MMM-yyyy");
    SimpleDateFormat time = new SimpleDateFormat("HH:mm:");

    String formattedDate = df.format(c);

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mStatusView = (TextView) findViewById(R.id.dbText);
        mStatusAvgView = (TextView) findViewById(R.id.dbAvgText);
        mStatus = (TextView) findViewById(R.id.dbStatus);


        if (runner == null) {
            runner = new Thread() {
                public void run() {
                    while (runner != null) {
                        try {
                            Thread.sleep(1000);
                            Log.i("Noise", "Tock");
                        } catch (InterruptedException e) {
                        }
                        ;
                        mHandler.post(updater);
                    }
                }
            };
            runner.start();
            Log.d("Noise", "start runner()");
        }
    }
    public void onResume() {
        super.onResume();
        startRecorder();
    }

    public void onPause() {
        super.onPause();
        //stopRecorder();
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

    public void updateTv() {
        Calendar calendar = Calendar.getInstance();
        int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);

        if (hourOfDay == 19){
            schedule = "night";
        }
        else if(hourOfDay == 8){
            schedule = "day";

        }else{
            schedule = "NA";
        }
        mStatus.setText(schedule);

        // mStatusView.setText(Double.toString((getAmplitudeEMA())) + " dB");
        double amplitude = mRecorder.getMaxAmplitude();
        if(amplitude > 0 && amplitude < 1000000) {
            double dbl = convertdDb(amplitude);
            mStatusView.setText(Double.toString(dbl)+ "dB");

            valuesAvg.add(dbl);
            lastTimestamp = System.currentTimeMillis() / 1000L;

            if(lastTimestamp - timestamp > 60 ){
                double sum = 0;
                int count = 0;

                for(Double value : valuesAvg) {
                    count++;
                    sum+= value;
                }
                valuesAvg = new ArrayList<>();
                timestamp = lastTimestamp;
                float average = (float) sum/count;

                mStatusAvgView.setText(String.format("%.2f", average)+ "dB");

                Date currentTime = Calendar.getInstance().getTime();

                if(schedule != "NA"){
                    Map<String, Object> avg = new HashMap<>();
                    double ans = Double.parseDouble(new DecimalFormat("##.##").format(average));

                    avg.put("value", ans);
                    avg.put("date", formattedDate);
                    avg.put("time", time.format(Calendar.getInstance().getTime()));
                    avg.put("schedule", schedule);
                }

            }
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
}
