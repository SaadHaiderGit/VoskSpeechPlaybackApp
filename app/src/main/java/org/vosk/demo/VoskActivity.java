// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class VoskActivity extends Activity implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;



    private final int BUFFER_SIZE = AudioTrack.getMinBufferSize(16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder;
    private AudioTrack audioTrack;
    private AudioRecord audioRecord = null;
    private boolean isRecording, isReplaying;
    private Button recordStart, recordStop, replayStart, replayStop;
    private TextView resultView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        resultView.setText(R.string.preparing);
        resultView.setMovementMethod(new ScrollingMovementMethod());
        //setUiState(STATE_START);

        //findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        //findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        //((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        recordStart = findViewById(R.id.start_recording);
        recordStop = findViewById(R.id.stop_recording);
        replayStart = findViewById(R.id.start_replay);
        replayStop = findViewById(R.id.stop_replay);

        recordStart.setEnabled(false);
        recordStop.setEnabled(false);
        replayStart.setEnabled(false);
        replayStop.setEnabled(false);

        recordStart.setOnClickListener(view -> startRecording());
        recordStop.setOnClickListener(view -> stopRecording());
        replayStart.setOnClickListener(view -> startReplay());
        replayStop.setOnClickListener(view -> stopReplay());


        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    //setUiState(STATE_READY);
                    resultView.setText(R.string.ready);
                    recordStart.setEnabled(true);
                    recordStop.setEnabled(true);
                    replayStart.setEnabled(true);
                    replayStop.setEnabled(true);

                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
        //setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        //setUiState(STATE_DONE);
    }

    /*private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                //((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                //((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                //findViewById(R.id.recognize_file).setEnabled(true);
                //findViewById(R.id.recognize_mic).setEnabled(true);
                //findViewById(R.id.pause).setEnabled((false));
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            case STATE_FILE:
                //((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText(getString(R.string.starting));
                //findViewById(R.id.recognize_mic).setEnabled(false);
                //findViewById(R.id.recognize_file).setEnabled(true);
                //findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                //((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                //findViewById(R.id.recognize_file).setEnabled(false);
                //findViewById(R.id.recognize_mic).setEnabled(true);
                //findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }*/

    private void setErrorState(String message) {
        resultView.setText(message);
        //((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        //findViewById(R.id.recognize_file).setEnabled(false);
        //findViewById(R.id.recognize_mic).setEnabled(false);
    }

    /*private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f);

                InputStream ais = getAssets().open(
                        "audio.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }


    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }*/


    private void startRecording() {
        print_log_v("Start Record button is clicked");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(VoskActivity.this, "You need mic permission to use this!", Toast.LENGTH_SHORT).show();
            return;
        }
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT));

        isRecording = true;
        audioRecord.startRecording();

        new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(getRecordingFilePath())) {
                byte[] buffer = new byte[1024];
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        fos.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void stopRecording() {
        if (audioRecord != null) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            if (speechStreamService != null) {
                //setUiState(STATE_DONE);
                speechStreamService.stop();
                speechStreamService = null;
            } else {
                //setUiState(STATE_FILE);
                try {
                    Recognizer rec = new Recognizer(model, 16000.f);

                    FileInputStream fis = new FileInputStream(getRecordingFilePath());

                    speechStreamService = new SpeechStreamService(rec, fis, 16000);
                    speechStreamService.start(this);
                } catch (IOException e) {
                    setErrorState(e.getMessage());
                }
            }
        }

    }


    private void startReplay() {
        print_log_v("Start Replay button is clicked");
        Toast.makeText(VoskActivity.this, "Now playing", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    16000,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    AudioRecord.getMinBufferSize(16000,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT),
                    AudioTrack.MODE_STREAM
            );

            try (FileInputStream fileInputStream = new FileInputStream(getRecordingFilePath())) {
                byte[] buffer = new byte[1024];
                int bytesRead;

                isReplaying = true;
                audioTrack.play();

                while (isReplaying && (bytesRead = fileInputStream.read(buffer)) != -1) {
                    audioTrack.write(buffer, 0, bytesRead);
                }

                if (audioTrack != null) {
                    isReplaying = false;
                }
                if (audioTrack != null) {
                    audioTrack.stop();
                }
                if (audioTrack != null) {
                    audioTrack.release();
                    audioTrack = null;
                }


            } catch (IOException e) {
                isReplaying = false;

                e.printStackTrace();
                print_log_v("This FAILED to run");
            }
        }).start();
    }


    private void stopReplay() {
        print_log_v("Stop Replay button is clicked");
        if (audioTrack != null) {
            isReplaying = false;
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
            Toast.makeText(VoskActivity.this, "Force-stopped playing", Toast.LENGTH_SHORT).show();
        }
    }


    private String getRecordingFilePath() {
        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
        File musicDirectory = contextWrapper.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File file = new File(musicDirectory,"audio"+".wav");
        print_log_v(file.getAbsolutePath());
        return file.getAbsolutePath();
    }


    private int print_log_v(String print_msg) {
        Log.v("Main Activity", print_msg);
        return 1;
    }
}
