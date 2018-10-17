package com.mozilla.speech;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Process;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.snips.hermes.InjectionKind;
import ai.snips.hermes.InjectionOperation;
import ai.snips.hermes.InjectionRequestMessage;
import ai.snips.hermes.IntentMessage;
import ai.snips.hermes.SessionEndedMessage;
import ai.snips.hermes.SessionQueuedMessage;
import ai.snips.hermes.SessionStartedMessage;
import ai.snips.platform.SnipsPlatformClient;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.FileEntity;
import cz.msebera.android.httpclient.entity.StringEntity;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public class MainActivity extends AppCompatActivity {

    private static final int AUDIO_ECHO_REQUEST = 0;
    private static final String TAG = "snips";

    private static final int FREQUENCY = 16_000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private SnipsPlatformClient client;
    private RequestQueue mRequestQueue;

    private AudioRecord recorder;
    Context app_context;

    private WebView webview;
    public Boolean snips_started = Boolean.FALSE;
    private File model_folder = new File(Environment.getExternalStorageDirectory().getPath()
            + "/wot_assistant/");
    private static final String HOME_PAGE = "https://speechcontrollerdemo.mozilla-iot.org/things";
    private static final String COMMANDS_URL = "https://speechcontrollerdemo.mozilla-iot.org/commands";
    private static final String OAUTH_TOKEN = "Bearer <add token>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ensurePermissions();
        app_context = this.getApplicationContext();
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);

        findViewById(R.id.start).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ensurePermissions()) {
                    final Button button = (Button) findViewById(R.id.start);
                    button.setEnabled(false);
                    button.setText(R.string.loading);

                    final View loadingPanel = findViewById(R.id.loadingPanel);
                    loadingPanel.setVisibility(View.VISIBLE);
                    injectIntent();
                }
            }
        });

        // load webview
        webview = findViewById(R.id.webview);
        webview.setWebViewClient(new WebViewClient());
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.loadUrl(HOME_PAGE);

        webview.setWebViewClient(new WebViewClient() {

            public void onPageFinished(WebView view, String url) {
                // do your stuff here
                if (!snips_started && model_folder.exists()) {
                    Log.d(TAG, "Starting snips");
                    startMegazordService();
                    snips_started = true;
                    Log.d(TAG, "Starting started");

                    // Record to the external cache directory for visibility
                    //mFileName = Environment.getExternalStorageDirectory().getPath()
                    //        + "/assistant-wot/";
                    //mFileName += "/audiorecordtest.3gp";

                    //callStt(mFileName);
                }
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void callStt(String videoPath){
        try
        {

            JSONObject jsonParams = new JSONObject();
            jsonParams.put("notes", "Test api support");
            FileEntity entity = new FileEntity(new File(videoPath));

            AsyncHttpClient client = new AsyncHttpClient();
            client.post(getApplicationContext(),"http://192.168.0.25:9001/", entity, "audio/3gpp",
                    new AsyncHttpResponseHandler() {

                @Override
                public void onStart() {
                    // called before request is started
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, byte[] response) {
                    // called when response HTTP status is "200 OK"
                    Log.d(TAG, "STT response ok:" + new String(response));
                    String json = new String(response);
                    try {
                        JSONObject reader = new JSONObject(json);
                        JSONArray results = reader.getJSONArray("data");
                        Log.d(TAG, "STT response transcription:" + results.getJSONObject(0).getString("text"));

                    } catch (Exception exc) {
                        Log.d(TAG, "Exception:" + exc.getMessage());
                    }
                }


                @Override
                public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
                    // called when response HTTP status is "4XX" (eg. 401, 403, 404)
                    Log.d(TAG, "STT response failure:" + String.valueOf(statusCode));

                }

                @Override
                public void onRetry(int retryNo) {
                    // called when request is retried
                }
            });

        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        if(client != null) {
            client.disconnect();
        }
        super.onDestroy();
    }

    private boolean ensurePermissions() {
        int status = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);
        if (status != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, AUDIO_ECHO_REQUEST);
            return false;
        }
        return true;
    }

    public RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            mRequestQueue = Volley.newRequestQueue(getApplicationContext());
        }
        return mRequestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }


    private void startMegazordService() {
        if (client == null) {
            // a dir where the assistant models was unziped. it should contain the folders asr dialogue hotword and nlu

            client = new SnipsPlatformClient.Builder(model_folder)
                    .enableDialogue(true) // defaults to true
                    .enableHotword(true) // defaults to true
                    .enableSnipsWatchHtml(true) // defaults to false
                    .enableLogs(true) // defaults to false
                    .withHotwordSensitivity(0.5f) // defaults to 0.5
                    .enableStreaming(true) // defaults to false
                    .enableInjection(true)
                    .build();

            client.setOnPlatformReady(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            final Button button = findViewById(R.id.start);
                            button.setEnabled(true);
                            button.setText(R.string.start_dialog_session);
                            button.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    // programmatically start a dialogue session
                                    client.startSession(null, new ArrayList<String>(), false, null);
                                }
                            });
                        }
                    });
                    return null;
                }
            });

            client.setOnHotwordDetectedListener(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    Log.d(TAG, "an hotword was detected !");
                    // Do your magic here :D
                    return null;
                }
            });

            client.setOnIntentDetectedListener(new Function1<IntentMessage, Unit>() {
                @Override
                public Unit invoke(IntentMessage intentMessage) {
                    Log.d(TAG, "received an intent: " + intentMessage);
                    // Do your magic here :D

                    JSONObject json = new JSONObject();
                    try {
                        json.put("text", intentMessage.getInput());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, "requesting: " + json.toString());

                    JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                            (Request.Method.POST, COMMANDS_URL, json, new Response.Listener<JSONObject>() {

                                @Override
                                public void onResponse(JSONObject response) {
                                    Log.d(TAG, "Response: " + response.toString());
                                }
                            }, new Response.ErrorListener() {

                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    // TODO: Handle error

                                }
                            }) {
                                @Override
                                public Map<String, String> getHeaders() throws AuthFailureError {
                                    Map<String, String> params = new HashMap<String, String>();
                                    params.put("Authorization", OAUTH_TOKEN);
                                    params.put("Accept", "application/json");

                                    return params;
                                }
                        };

                    // Access the RequestQueue through your singleton class.
                    addToRequestQueue(jsonObjectRequest);

                    // For now, lets just use a random sentence to tell the user we understood but don't know what to do
                    client.endSession(intentMessage.getSessionId(), intentMessage.getInput());
                    return null;
                }
            });

            client.setOnListeningStateChangedListener(new Function1<Boolean, Unit>() {
                @Override
                public Unit invoke(Boolean isListening) {
                    Log.d(TAG, "asr listening state: " + isListening);
                    // Do you magic here :D
                    return null;
                }
            });

            client.setOnSessionStartedListener(new Function1<SessionStartedMessage, Unit>() {
                @Override
                public Unit invoke(SessionStartedMessage sessionStartedMessage) {
                    Log.d(TAG, "dialogue session started: " + sessionStartedMessage);
                    return null;
                }
            });

            client.setOnSessionQueuedListener(new Function1<SessionQueuedMessage, Unit>() {
                @Override
                public Unit invoke(SessionQueuedMessage sessionQueuedMessage) {
                    Log.d(TAG, "dialogue session queued: " + sessionQueuedMessage);
                    return null;
                }
            });

            client.setOnSessionEndedListener(new Function1<SessionEndedMessage, Unit>() {
                @Override
                public Unit invoke(SessionEndedMessage sessionEndedMessage) {
                    Log.d(TAG, "dialogue session ended: " + sessionEndedMessage);
                    return null;
                }
            });

            // This api is really for debugging purposes and you should not have features depending on its output
            // If you need us to expose more APIs please do ask !
            client.setOnSnipsWatchListener(new Function1<String, Unit>() {
                public Unit invoke(final String s) {
                    Log.d(TAG, s);
                    return null;
                }
            });

            // We enabled steaming in the builder, so we need to provide the platform an audio stream. If you don't want
            // to manage the audio stream do no enable the option, and the snips platform will grab the mic by itself
            startStreaming();

            client.connect(this.getApplicationContext());
        }
    }

    private void injectIntent() {
        // inject new values in the "house_room" entity
        HashMap<String, List<String>> values = new HashMap<>();
        values.put("house_room", Arrays.asList("bunker", "batcave"));
        client.requestInjection(new InjectionRequestMessage(
                Collections.singletonList(new InjectionOperation(InjectionKind.Add, values)),
                new HashMap<String, List<String>>()));
    }

    private volatile boolean continueStreaming = true;

    private void startStreaming() {
        continueStreaming = true;
        new Thread() {
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                runStreaming();
            }
        }.start();
    }

    private void runStreaming() {
        Log.d(TAG, "starting audio streaming");
        final int minBufferSizeInBytes = AudioRecord.getMinBufferSize(FREQUENCY, CHANNEL, ENCODING);
        Log.d(TAG, "minBufferSizeInBytes: " + minBufferSizeInBytes);

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, FREQUENCY, CHANNEL, ENCODING, minBufferSizeInBytes);
        recorder.startRecording();

        while (continueStreaming) {
            short[] buffer = new short[minBufferSizeInBytes / 2];
            recorder.read(buffer, 0, buffer.length);
            if (client != null) {
                client.sendAudioBuffer(buffer);
            }
        }
        recorder.stop();
        Log.d(TAG, "audio streaming stopped");
    }


    @Override
    protected void onResume() {
        super.onResume();
        if(client != null) {
            startStreaming();
            client.resume();
        }
    }

    @Override
    protected void onPause() {
        continueStreaming = false;
        if (client != null) {
            client.pause();
        }
        super.onPause();
    }
}
