/*
  Copyright 2017, Google Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package com.example.speech;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.*;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.lang.String.format;
import static java.lang.System.out;
import static java.nio.file.StandardOpenOption.READ;

/**
 * StreamRecognition for InputStream from file, reading by 0.5 second chunks with intermediate recognition results
 */
public class RecognizeStreamFromFile {
  private static final AudioEncoding ENCODING = AudioEncoding.LINEAR16;
  private static final int SAMPLE_SIZE_BITS = 16; // for AudioEncoding.LINEAR16
  private static final int SAMPLE_SIZE_BYTES = SAMPLE_SIZE_BITS / 8;
//  private static final int SAMPLE_RATE = 8000; // 8kHz audio
  private static final int SAMPLE_RATE = 16000; // 16kHz audio
  private static final int BYTES_IN_RECORD_SECOND = SAMPLE_SIZE_BYTES * SAMPLE_RATE;
  private static final int BUFFER_SIZE = BYTES_IN_RECORD_SECOND / 2; // buffer for 0.5 seconds of speech

//  private static final String LANG="ru-RU";
  private static final String LANG="en-US";

  public static void main(String... args) throws Exception {
    String fileName = "/home/artyom/projects/google-labs/java-docs-samples/speech/cloud-client/resources/audio.raw";
//    String fileName = "/home/artyom/Documents/27689583775368116522423062614_yn.wav";
    streamingRecognizeFile(fileName);
  }

  /**
   * Performs streaming speech recognition on raw PCM audio data.
   *
   * @param fileName the path to a PCM audio file to transcribe.
   */
  public static void streamingRecognizeFile(String fileName) throws Exception, IOException {
    Path path = Paths.get(fileName);

    // Instantiates a client with GOOGLE_APPLICATION_CREDENTIALS
    SpeechClient speech = SpeechClient.create();

    // Configure request with local raw PCM audio
    RecognitionConfig recConfig = RecognitionConfig.newBuilder()
        .setEncoding(ENCODING)
        .setLanguageCode(LANG)
        .setSampleRateHertz(SAMPLE_RATE)
        .build();
    StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
        .setConfig(recConfig)
        .setInterimResults(true) // To get messages for intermediate results (live recognition)
        .build();

    ResponseApiStreamingObserver<StreamingRecognizeResponse> responseObserver =
        new ResponseApiStreamingObserver<StreamingRecognizeResponse>();

    BidiStreamingCallable<StreamingRecognizeRequest,StreamingRecognizeResponse> callable =
        speech.streamingRecognizeCallable();

    ApiStreamObserver<StreamingRecognizeRequest> requestObserver =
        callable.bidiStreamingCall(responseObserver);

    // The first request must **only** contain the audio configuration:
    requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
        .setStreamingConfig(config)
        .build());

    // Subsequent requests must **only** contain the audio data.
    try {
      InputStream speechStream = Files.newInputStream(path,READ);
      int numBytesRead = 0;
      int total = 0;
      byte[] readBuffer = new byte[BUFFER_SIZE];
      while (speechStream.available()>0) {
        try {
          numBytesRead = speechStream.read(readBuffer, 0, Math.min(BUFFER_SIZE,speechStream.available()));
        } catch (IndexOutOfBoundsException e){
          break;
        } catch (IOException e) {
          e.printStackTrace();
          break;
        }
        if (numBytesRead == -1) break;
        total += numBytesRead;
        out.println(format("total bytes read: %s",total));

        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(readBuffer))
                .build());
        out.println("speech sent");
        Thread.sleep(500);
      }
    } catch (IOException e) {
      e.printStackTrace();
      //throw new IllegalStateException(e);
    }

    // Mark transmission as completed after sending the data.
    requestObserver.onCompleted();

    List<StreamingRecognizeResponse> responses = responseObserver.future().get();

    for (StreamingRecognizeResponse response: responses) {
      // For streaming recognize, the results list has one is_final result (if available) followed
      // by a number of in-progress results (if iterim_results is true) for subsequent utterances.
      // Just print the first result here.
      StreamingRecognitionResult result = response.getResultsList().get(0);
      // There can be several alternative transcripts for a given chunk of speech. Just use the
      // first (most likely) one here.
      SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
      out.println(alternative.getTranscript());
    }
    speech.close();
  }

  private static class ResponseApiStreamingObserver<T> implements ApiStreamObserver<T> {
    private final SettableFuture<List<T>> future = SettableFuture.create();
    private final List<T> messages = new java.util.ArrayList<>();

    @Override
    public void onNext(T message) {
      out.println("msg received "+message);
      messages.add(message);
    }

    @Override
    public void onError(Throwable t) {
      future.setException(t);
    }

    @Override
    public void onCompleted() {
      future.set(messages);
    }

    // Returns the SettableFuture object to get received messages / exceptions.
    public SettableFuture<List<T>> future() {
      return future;
    }
  }

}
