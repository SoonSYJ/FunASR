/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.helloworldexample;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.Image;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import ex.grpc.ASRGrpc;
import ex.grpc.Paraformer.Response;
import ex.grpc.Paraformer.Request;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bumptech.glide.Glide;
import com.google.protobuf.ByteString;


public class HelloworldActivity extends AppCompatActivity {
  // controller
  private Button sendButton;
  private Button trymeButton;
  private EditText hostEdit;
  private EditText portEdit;
  private EditText fileEdit;
  private TextView resultText;
  private EditText resultEdit;
  private ImageView gifView;

  // grpc settings
  private static final String LOG_TAG = "gRPC";
  private static final String DEFAULT_HOST = "10.78.2.132";
  private static final String DEFAULT_PORT = "10095";
  private static final String DEFAULT_FILE = "result";

  // audio recorder settings
  private final int MY_PERMISSIONS_RECORD_AUDIO = 1;
  private static final int SAMPLE_RATE = 16000;  // The sampling rate
  private static final int MAX_QUEUE_SIZE = 5000;
  private int miniBufferSize = 0;  // 1280 bytes 648 byte 40ms, 0.04s
  private AudioRecord record = null;
  private static final BlockingQueue<byte[]> audioBufferQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);
  private final BlockingQueue<short[]> asrBufferQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE / 2);
  private boolean startRecord = false;

  // tts settings
  private static byte[] ttsBytes;
  private AudioTrack mAudioTrack = null;
  private static final int audioTrackMode = AudioTrack.MODE_STREAM;
  private int miniBufferSizeTack = 0;

  // stream asr settings
  private static final List<String> resource = Arrays.asList(
          "final.zip", "units.txt", "ctc.ort", "decoder.ort", "encoder.ort", "context.txt"
  );

  // quote settings
  private static final HashMap<String, String> quoteMap = new HashMap<String, String>();

  public static void assetsInit(Context context) throws IOException {
    AssetManager assetMgr = context.getAssets();
    // Unzip all files in resource from assets to context.
    // Note: Uninstall the APP will remove the resource files in the context.
    for (String file : assetMgr.list("")) {
      if (resource.contains(file)) {
        File dst = new File(context.getFilesDir(), file);
        if (!dst.exists() || dst.length() == 0) {
          Log.i(LOG_TAG, "Unzipping " + file + " to " + dst.getAbsolutePath());
          InputStream is = assetMgr.open(file);
          OutputStream os = new FileOutputStream(dst);
          byte[] buffer = new byte[4 * 1024];
          int read;
          while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
          }
          os.flush();
        }
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_helloworld);
    sendButton = (Button) findViewById(R.id.send_button);
    trymeButton = (Button) findViewById(R.id.tryme_button);
    hostEdit = (EditText) findViewById(R.id.host_edit_text);
    portEdit = (EditText) findViewById(R.id.port_edit_text);
    fileEdit = (EditText) findViewById(R.id.file_edit_text);

    resultText = (TextView) findViewById(R.id.grpc_response_text);
    resultText.setMovementMethod(new ScrollingMovementMethod());
    resultEdit = (EditText) findViewById(R.id.result_text);
    gifView = (ImageView) findViewById(R.id.gif_view);

    Glide.with(this)
            .load(assetFilePath(this, "paraformer.png"))
            .into(gifView);

    // default server host & port
    hostEdit.setText(DEFAULT_HOST);
    portEdit.setText(DEFAULT_PORT);
    fileEdit.setText(DEFAULT_FILE);

    quoteMap.put("现代名言", "1");
    quoteMap.put("现代文学", "2");
    quoteMap.put("现代诗歌", "3");
    quoteMap.put("现代动漫", "4");
    quoteMap.put("现代影视剧", "5");
    quoteMap.put("现代网络", "6");
    quoteMap.put("古诗", "7");
    quoteMap.put("歇后语", "8");

    requestAudioPermissions();

    try {
      assetsInit(this);
    } catch (IOException e) {
      Log.e(LOG_TAG, "Error process asset files to file path");
    }
    Recognize.init(getFilesDir().getPath(), false);

    Button button = findViewById(R.id.record_button);  // get button controller
    button.setText("Start Record");  // set button text
    button.setOnClickListener(view -> {  // watch if button is touched
      if (!startRecord) {
        startRecord = true;  // set recording flag
        startRecordThread();  // start recorder
        Recognize.reset();  // reset ASR engine
        startAsrThread();  // start the engine
        Recognize.startDecode();  // start ASR decoding
        button.setText("Stop Record");  // set button text
        Glide.with(this)
                .load(assetFilePath(this, "recording_w.gif"))
                .into(gifView);
      } else {
        startRecord = false;  // set recording flag
        button.setText("Start Record");  // set button text
        Recognize.setInputFinished();  // stop ASR engine
        Glide.with(this)
                .load(assetFilePath(this, "paraformer.png"))
                .into(gifView);
      }
    });
  }

  private void requestAudioPermissions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this,
              new String[]{
                      Manifest.permission.RECORD_AUDIO,
                      Manifest.permission.WRITE_EXTERNAL_STORAGE},
              MY_PERMISSIONS_RECORD_AUDIO);
    } else {
      initRecorder();
      initAudioTrack();
    }
  }

  public void initAudioTrack(){
    miniBufferSizeTack = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,//采样率
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
    );
    mAudioTrack = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
            .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
            .setTransferMode(audioTrackMode)
            .setBufferSizeInBytes(miniBufferSizeTack)
            .build();
  }

  private void initRecorder() {
    // buffer size in bytes 1280
    miniBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);
    if (miniBufferSize == AudioRecord.ERROR || miniBufferSize == AudioRecord.ERROR_BAD_VALUE) {
      Log.e(LOG_TAG, "Audio buffer can't initialize!");
      return;
    }
    record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            miniBufferSize);
    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
      Log.e(LOG_TAG, "Audio Record can't initialize!");
      return;
    }
    Log.i(LOG_TAG, "Record init okay");
  }

  private void startRecordThread() {
    new Thread(() -> {
      record.startRecording();
      Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
      while (startRecord) {
        byte[] buffer = new byte[miniBufferSize]; // 1280 samples
        int read = record.read(buffer, 0, buffer.length);
        try {
          if (AudioRecord.ERROR_INVALID_OPERATION != read) {
            audioBufferQueue.put(buffer);
            short[] asrBuffer = new short[miniBufferSize / 2]; // 640 samples
            for (int i = 0; i < miniBufferSize / 2 ; i++){
              asrBuffer[i] = (short) ((buffer[i*2] & 0xff) | ((buffer[i*2+1] & 0xff) << 8));
            }
            asrBufferQueue.put(asrBuffer);
          }
        } catch (InterruptedException e) {
          Log.e(LOG_TAG, e.getMessage());
        }
        Button button = findViewById(R.id.record_button);
        if (!button.isEnabled() && startRecord) {
          runOnUiThread(() -> button.setEnabled(true));
        }
      }
      record.stop();
    }).start();
  }

  private void startAsrThread() {
    new Thread(() -> {
      // Send all data
      while (startRecord || asrBufferQueue.size() > 0) {
        try {
          short[] data = asrBufferQueue.take();
          // 1. add data to C++ interface
          Recognize.acceptWaveform(data);
          // 2. get partial result
          runOnUiThread(() -> {
            EditText resultEdit = findViewById(R.id.result_text);
            resultEdit.setText(Recognize.getResult());
          });
        } catch (InterruptedException e) {
          Log.e(LOG_TAG, e.getMessage());
        }
      }
//      sendButton.setEnabled(false);
//      resultText.setText("");
//            resultEdit.setText("");
      new GrpcTask(this)
              .execute(
                      hostEdit.getText().toString(),
                      portEdit.getText().toString(),
                      "ASR");
      // Wait for final result
//      while (true) {
//        // get result
//        if (!Recognize.getFinished()) {
//          runOnUiThread(() -> {
//            EditText resultEdit = findViewById(R.id.result_text);
//            resultEdit.setText(Recognize.getResult());
//          });
//        } else {
//          runOnUiThread(() -> {
//            Button button = findViewById(R.id.record_button);
//            button.setEnabled(true);
//
//            sendButton.setEnabled(false);
//            resultText.setText("");
////            resultEdit.setText("");
//            new GrpcTask(this)
//                    .execute(
//                            hostEdit.getText().toString(),
//                            portEdit.getText().toString(),
//                            "ASR");
//          });
//          break;
//        }
//      }

    }).start();
  }

  public void sendMessage(View view) {
    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(hostEdit.getWindowToken(), 0);
    sendButton.setEnabled(false);
    resultText.setText("");
    resultEdit.setText("");
    new GrpcTask(this)
        .execute(
                hostEdit.getText().toString(),
                portEdit.getText().toString(),
                "ASR");
  }

  public void tryMe(View view) {
    ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(hostEdit.getWindowToken(), 0);
    trymeButton.setEnabled(false);

    resultText.setText("");
//    resultEdit.setText("");
    new GrpcTask(this)
            .execute(
                    hostEdit.getText().toString(),
                    portEdit.getText().toString(),
                    "TRYME");
  }

  public void saveResult(View view) {
    String final_res = resultEdit.getText().toString();
    try {
      File file = new File(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                      .getAbsolutePath() + "/" + fileEdit.getText().toString() +  ".txt");
      if (!file.exists()) {
        file.createNewFile();
      }
      // file append
      RandomAccessFile fos = new RandomAccessFile(file, "rw");
      fos.seek(file.length());
      fos.write((final_res).getBytes());
      fos.write("\n".getBytes());
      fos.close();

      resultText.setText("Save file result done!");
      resultEdit.setText("");
    } catch (Exception e) {
      // TODO Auto-generated catch block
      resultText.setText("Save file result failed!");
      e.printStackTrace();
    }
  }

  private class GrpcTask extends AsyncTask<String, Void, String> {
    private final WeakReference<Activity> activityReference;
    private ManagedChannel channel;

    private GrpcTask(Activity activity) {
      this.activityReference = new WeakReference<Activity>(activity);
    }

    @Override
    protected String doInBackground(String... params) {
      String host = params[0];
      String portStr = params[1];
      String taskType = params[2];

      final String[] result = {"failed"};

      int port = TextUtils.isEmpty(portStr) ? 0 : Integer.valueOf(portStr);
      try {
        final StringBuffer logs = new StringBuffer();
        final CountDownLatch finishLatch = new CountDownLatch(1);

        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        ASRGrpc.ASRStub stub = ASRGrpc.newStub(channel);

        StreamObserver<Request> requestObserver =
          stub.recognize(new StreamObserver<Response>() {
            @Override
            public void onNext(Response note) {
              if (note.getAction().equals("finish")) {
                ttsBytes = note.getAudioData().toByteArray();
                result[0] = note.getSentence();
              }
            }

            @Override
            public void onError(Throwable t) {
              finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
              finishLatch.countDown();
            }
          });

        try {
          if (taskType == "ASR") {
            while (audioBufferQueue.size() > 0) {
              requestObserver.onNext(newRequest("project1_user1", "CN", true, false));
            }
            requestObserver.onNext(newRequest("project1_user1", "CN", false, false));  // start model inference
            requestObserver.onNext(newRequest("project1_user1", "CN", false, true));   // return final result
          } else if (taskType == "TRYME") {
            Activity activity = activityReference.get();
            EditText resultEdit = (EditText) activity.findViewById(R.id.result_text);
            String trymeText = resultEdit.getText().toString();
            Spinner indexSpinner = (Spinner) activity.findViewById(R.id.tryme_index);
            String a = indexSpinner.getSelectedItem().toString();
            String quoteIndex = quoteMap.get(a);
            trymeText = quoteIndex + "#" + trymeText;
            requestObserver.onNext(newRequest("project1_user1", trymeText, false, true));
          }
        } catch (RuntimeException e) {
          // Cancel RPC
          requestObserver.onError(e);
          throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // Receiving happens asynchronously
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
          throw new RuntimeException(
                  "Could not finish rpc within 1 minute, the server is likely down");
        }

        return result[0];
      } catch (Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return String.format("Failed... : %n%s", sw);
      }
    }

    @Override
    protected void onPostExecute(String result) {
      try {
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      Activity activity = activityReference.get();
      if (activity == null) {
        return;
      }
      TextView resultText = (TextView) activity.findViewById(R.id.grpc_response_text);
      EditText resultEdit = (EditText) activity.findViewById(R.id.result_text);

      Button sendButton = (Button) activity.findViewById(R.id.send_button);
      Button trymeButton = (Button) activity.findViewById(R.id.tryme_button);

      resultText.setText(result);

      JSONObject jo = JSON.parseObject(result);
      String detail = (String) jo.get("detail");
      String text = "";
      if (detail.equals("tryme")) {
        // tryme flow
        JSONObject joq = (JSONObject) jo.get("text");
        String quote = (String) joq.get("quote");
        String author = (String) joq.get("author");
        String work = (String) joq.get("work");

        text = "<<" + work + ">>" + "\n--" + author + "\n" + quote;
        if(mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING){
          mAudioTrack.stop();
          mAudioTrack.flush();
        }
        mAudioTrack.reloadStaticData();
        int loopCount = ttsBytes.length / miniBufferSizeTack;
        int size = 0;
        for (int i = 0 ; i < loopCount ; i++){
          mAudioTrack.play();
          size = mAudioTrack.write(ttsBytes, i * miniBufferSizeTack, miniBufferSizeTack, AudioTrack.WRITE_BLOCKING);
        }

      } else {
        // ASR flow
        text = (String) jo.get("text");
      }

      resultEdit.setText(text);

      sendButton.setEnabled(true);
      trymeButton.setEnabled(true);
    }
  }

  private static Request newRequest(String user, String language, Boolean speaking, Boolean isEnd) {
    try {
      ByteString audio_data = ByteString.copyFrom("".getBytes());
      if (speaking) {
        byte[] data = audioBufferQueue.take();
        audio_data = ByteString.copyFrom(data);
      }

      return Request.newBuilder()
              .setAudioData(audio_data)
              .setUser(user)
              .setLanguage(language)
              .setSpeaking(speaking)
              .setIsEnd(isEnd)
              .build();
    } catch (InterruptedException e) {
      Log.e(LOG_TAG, e.getMessage());
      return null;
    }
  }
  private static String assetFilePath(Context context, String assetName) {
    File file = new File(context.getFilesDir(), assetName);
    if (file.exists() && file.length() > 0) {
      return file.getAbsolutePath();
    }

    try (InputStream is = context.getAssets().open(assetName)) {
      try (OutputStream os = new FileOutputStream(file)) {
        byte[] buffer = new byte[4 * 1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
          os.write(buffer, 0, read);
        }
        os.flush();
      }
      return file.getAbsolutePath();
    } catch (IOException e) {
      Log.e(LOG_TAG, assetName + ": " + e.getLocalizedMessage());
    }
    return null;
  }
}
