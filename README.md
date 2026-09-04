# Robot Voice Assistant

Android voice assistant for a university project.

## Runtime flow

1. Persian speech is recognized locally with Vosk.
2. The recognized text is sent directly from the Android app to OpenRouter.
3. OpenRouter returns the AI response.
4. The response is displayed in the app.
5. Android Text-to-Speech reads the response aloud.

## Standalone mode

This build does **not** require a Node.js/Python backend, localhost, emulator networking, or a computer running a server. The phone only needs an Internet connection to reach OpenRouter.

The app uses OpenRouter's `openrouter/free` router, which automatically selects an available free model. OpenRouter documents this router as a zero-cost option with changing availability and rate limits.

## Vosk model

The GitHub Actions workflow downloads `vosk-model-small-fa-0.42` during the APK build and places it under:

`app/src/main/assets/model-fa`

The APK therefore contains the speech-recognition model and does not need to download it on the phone.

## API key

For this university/demo build, the OpenRouter API key is intentionally embedded in `app/src/main/java/com/example/robotvoice/OpenAIClient.java` as requested. This is **not secure for production** because an APK can be reverse-engineered and the key can be extracted.

For a real/public release, use a backend or another secure credential strategy and rotate any key that has been exposed in source control.

## Build on GitHub

Use GitHub Actions:

`Actions -> Build Android APK -> Run workflow`

Then download the `robot-voice-debug-apk` artifact.
