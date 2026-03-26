# Muninn-Weather

Muninn-Weather is a minimal Android app that polls Home Assistant for weather data and broadcasts it to Gadgetbridge for PineTime.

## Setup

1. Build and install the app on your phone.
2. On first launch, enter your Home Assistant URL (HTTPS) and a long-lived access token.
3. The app schedules a background sync every 15 minutes and sends updates to Gadgetbridge.

## Build and Run

- Open the project in Android Studio and let Gradle sync.
- Run the app on a device with Gadgetbridge installed.

## Docker Build

- Build and run the containerized build: `docker compose run --rm android-build`
- The APK will be in `app/build/outputs/apk/debug/` on your host machine.
- The Docker build uses `linux/amd64` to avoid ARM AAPT2 issues on Apple Silicon.

## Notes

- Home Assistant entity: `sensor.roof_top_weather_station_temperature`
- Gadgetbridge intent action: `nodomain.freeyourgadget.gadgetbridge.action.WEATHER`
