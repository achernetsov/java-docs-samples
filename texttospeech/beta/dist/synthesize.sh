#!/usr/bin/env bash
#     mvn package exec:java -Dexec.mainClass='com.example.texttospeech.SynthesizeFile' -Dexec.args='--text resources/hello.txt'

GOOGLE_APPLICATION_CREDENTIALS=$(pwd)/secret/secret.json \
java -cp tts-samples-1.0.11.jar com.example.texttospeech.SynthesizeFile text hello.txt