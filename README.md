# Muninn-Weather

Muninn-Weather is a minimal Android app that polls Home Assistant for weather data and broadcasts it to Gadgetbridge for PineTime. A simple app for a simple task.

## Setup

1. Download from Github under releases or use Obtanium.
2. On open, click the "Configure Home Assistant" Button
    1. Provide the URL for your Home Assistant instance and a long-lived access token.
    2. Select a sensor to use as your temperature sensor. 
3. The app schedules a background sync every 15 minutes and sends updates to Gadgetbridge.

## Docker Build

- Build and run the containerized build: `docker compose run --rm android-build`
- The APK will be in `app/build/outputs/apk/debug/` on your host machine.
- The Docker build uses `linux/amd64` to avoid ARM AAPT2 issues on Apple Silicon.

## Notes

- Gadgetbridge intent action: `nodomain.freeyourgadget.gadgetbridge.action.WEATHER`
