#!/usr/bin/env bash
GOOGLE_APPLICATION_CREDENTIALS=$(pwd)/secret/secret.json \
    mvn package exec:java -Dexec.mainClass='com.example.texttospeech.SynthesizeFile' -Dexec.args='--text resources/hello.txt'