package de.glassroom.grt;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import de.glassroom.grt.glassroomrecordingtool.R;
import de.glassroom.grt.wf.Command;
import de.glassroom.grt.wf.Workflow;
import de.glassroom.grt.wf.WorkflowParser;

public class MainActivity extends Activity {
    private static final int TAKE_PICTURE_INTENT = 1;
    private static final int CAPTURE_VIDEO_INTENT = 2;

    private RelativeLayout container;
    private WebView webView;
    private WorkflowHandler workflowHandler;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private List<Command> validVoiceCommands;
    private boolean isListening;
    private Handler mainHandler;
    private TextRecognitionHandler textRecognizedHandler;
    private int recognitionRetry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    // Log.d("Lifecycle", "onCreate()");

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        container = (RelativeLayout) findViewById(R.id.container);
        webView = (WebView) findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptEnabled(true);
        // TODO Activate to fix display on Vuzix
        webView.getSettings().setDefaultZoom(WebSettings.ZoomDensity.CLOSE);

        mainHandler = new Handler(getApplicationContext().getMainLooper());

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getApplication().getPackageName());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE");

        speechRecognizer.setRecognitionListener(prepareRegnitionListener());
        isListening = false;
        recognitionRetry = 0;

        Workflow workflow = loadWorkflow(getResources().getAssets());
        workflowHandler = new WorkflowHandler(this, webView, workflow);

    }

    private RecognitionListener prepareRegnitionListener() {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:GRT.onVoiceReady();");
                    }
                });
            }

            @Override
            public void onBeginningOfSpeech() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:GRT.onVoiceActive();");
                    }
                });
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Do nothing.
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Do nothing.
            }

            @Override
            public void onEndOfSpeech() {
                // Do nothing.
            }

            @Override
            public void onError(int error) {
                String errorText;
                isListening = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:GRT.onVoiceError();");
                    }
                });
                boolean throwError = false;
                switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO:
                        if (recognitionRetry < 5) {
                            Log.e("MainActivity", "Failed to recognize speech: Audio recording error. Retrying ...");
                            recognitionRetry++;
                            startListening(2000);
                        } else {
                            Log.e("MainActivity", "Failed to recognize speech: Audio recording error. Aborting.");
                            throwError = true;
                        }
                        break;
                    case SpeechRecognizer.ERROR_CLIENT:
                        Log.e("MainActivity", "Failed to recognize speech: Other client side errors. Aborting.");
                        throwError = true;
                        break;
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        Log.e("MainActivity", "Failed to recognize speech: Insufficient permissions. Aborting.");
                        throwError = true;
                        break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        Log.w("MainActivity", "Failed to recognize speech: Other network related errors. Aborting.");
                        throwError = true;
                        break;
                    case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        if (recognitionRetry < 5) {
                            Log.w("MainActivity", "Failed to recognize speech: Network operation timed out. Retrying ...");
                            recognitionRetry++;
                            startListening(2000);
                        } else {
                            Log.w("MainActivity", "Failed to recognize speech: Network operation timed out. Aborting.");
                            throwError = true;
                        }
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        Log.d("MainActivity", "Failed to recognize speech: No recognition results matched. Retrying ...");
                        startListening(0);
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        Log.i("MainActivity", "Failed to recognize speech: Recognition service busy. Retrying ...");
                        if (recognitionRetry < 5) {
                            recognitionRetry++;
                            startListening(1000);
                        } else {
                            throwError = true;
                        }
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        Log.w("MainActivity", "Failed to recognize speech: Server sends error status.");
                        throwError = true;
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        Log.d("MainActivity", "Failed to recognize speech: No speech input. Retrying ...");
                        startListening(0);
                        break;
                    default:
                        Log.e("MainActivity", "Failed to recognize speech: Unknown error.");
                        throwError = true;
                }
                if (throwError && textRecognizedHandler != null) {
                    textRecognizedHandler.onError();
                }
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                recognitionRetry = 0;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                Log.d("MainActivity", "Completed speech recognition. Result: " + matches);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:GRT.onVoiceInactive();");
                    }
                });

                if (matches.isEmpty()) {
                    startListening(0);
                    return;
                }
                if (validVoiceCommands != null) {
                    for (Command command : validVoiceCommands) {
                        for (String voiceCommand : command.getVoiceCommands()) {
                            for (String match : matches) {
                                if (match.equalsIgnoreCase(voiceCommand)) {
                                    workflowHandler.execute(command.getKey());
                                    return;
                                }
                            }
                        }
                    }
                    Log.d("MainActivity", "No matching voice command. Retrying ...");
                    startListening(0);
                } else {
                    String match = matches.get(0);
                    Log.i("MainActivity", "Primary match for text: " + match);
                    if (textRecognizedHandler == null) {
                        Log.e("MainActivity", "No handler for recognized text.");
                        return;
                    }
                    textRecognizedHandler.textRecognized(match.substring(0,1).toUpperCase() + match.substring(1));
                    textRecognizedHandler = null;
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Do nothing.
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Do nothing.
            }
        };
    }

    private static Workflow loadWorkflow(AssetManager assetManager) {
        String line;
        Workflow wf = null;

        try {
            InputStream is = assetManager.open("workflow/workflow.xml", AssetManager.ACCESS_STREAMING);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            String workflowXML = "";
            while ((line = br.readLine()) != null) {
                workflowXML += line;
            }
            wf = WorkflowParser.parseWorkflow(workflowXML);
            Log.i("MainActivity", "Workflow initialized.");
        } catch (FileNotFoundException e) {
            Log.e("MainActivity", "Failed to open workflow.xml file.", e);
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to open workflow.xml file.", e);
        }

        return wf;
    }

    @Override
    public void onDestroy() {
        //Log.d("Lifecycle", "onDestroy()");
        speechRecognizer.destroy();
        super.onDestroy();
        finish();
    }

    public void startTakePictureActivity(File tempFile) {
        Intent intent = new Intent(this, TakePictureActivity.class);
        intent.putExtra("path", tempFile);
        startActivityForResult(intent, TAKE_PICTURE_INTENT);
    }

    public void startCaptureVideo(File tempFile) {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(tempFile));
        // intent.putExtra(MediaStore.EXTRA_SHOW_ACTION_ICONS, false);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, CAPTURE_VIDEO_INTENT);
        } else {
            Log.e("MainActivity", "Failed to start video capture: Unknown intent.");
        }
    }

    public void listenToVoiceCommand(List<Command> validVoiceCommands) throws IllegalStateException {
        if (isListening) {
            throw new IllegalStateException("A recognition task is already running.");
        }
        this.validVoiceCommands = validVoiceCommands;
        startListening(0);
    }

    public void reinitializeVoiceCommands() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isListening) {
                    speechRecognizer.cancel();
                }
                recognitionRetry = 0;
                speechRecognizer.startListening(speechRecognizerIntent);
                Log.d("MainActivity", "Reinitialized voice listener.");
            }
        });
    }

    public void recognizeSpeech(TextRecognitionHandler handler) throws IllegalArgumentException {
        if (isListening) {
            throw new IllegalStateException("A recognition task is already running.");
        }
        this.validVoiceCommands = null;
        this.textRecognizedHandler = handler;
        startListening(0);
    }

    public void stopRecognizing() {
        if (isListening) mainHandler.post(new Runnable() {
            @Override
            public void run() {
                speechRecognizer.cancel();
                isListening = false;
                validVoiceCommands = null;
                Log.d("MainActivity", "Stop listing to speech.");
            }
        });
    }

    private void startListening(int delay) {
        isListening = true;
        recognitionRetry = 0;
        if (delay > 0) {
            Timer t = new Timer();
            t.schedule(new TimerTask() {
                @Override
                public void run() {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            speechRecognizer.startListening(speechRecognizerIntent);
                            Log.d("MainActivity", "Start delayed listening to speech.");
                        }
                    });
                }
            }, delay);
        } else {
            speechRecognizer.startListening(speechRecognizerIntent);
            Log.d("MainActivity", "Start instant listening to speech.");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case TAKE_PICTURE_INTENT:
                workflowHandler.handlePictureTaken(resultCode);
                break;
            case CAPTURE_VIDEO_INTENT:
                // Uri videoUri = intent.getData();
                workflowHandler.handleCapturedVideo(resultCode);
                break;
            default:
                // Do nothing.
        }
    }

    @Override
    protected void onStart() {
        //Log.d("Lifecycle", "onStart()");
        super.onStart();
    }

    @Override
    protected void onRestart() {
        // Log.d("Lifecycle", "onRestart()");
        super.onRestart();
    }

    @Override
    protected void onResume() {
        // Log.d("Lifecycle", "onResume()");
        super.onResume();
    }

    @Override
    protected void onPause() {
        // Log.d("Lifecycle", "onPause()");
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Log.d("Lifecycle", "onStop()");
        super.onStop();
    }
}
