# Tune

Tune is an offline guitar tuner for Android 10 and newer. It listens through the phone's microphone, finds the closest string in the selected tuning, and shows how far that string is from its target pitch.

## Install

Download `Tune-v<version>.apk` from the project's GitHub Releases page and open it on your Android phone. If Android blocks the installation, allow APK installation for the app you used to open the file. Grant microphone access when Tune asks for it.

The Android package is `dev.mangelov.tune`. An older installation under `dev.angelov.tune` remains a separate app, and its saved settings do not transfer automatically.

## Tune a string

1. Choose a tuning at the top of the screen. Standard tuning is selected by default.
2. Pluck one string at a time near the phone. Tune shows its string number, note, and measured frequency above the needle. String 6 is the lowest; string 1 is the highest.
3. Follow the reading below the needle: **TIGHTEN** raises a flat string's pitch, and **LOOSEN** lowers a sharp string's pitch. The needle moves left for flat and right for sharp.
4. When the pitch is within 5 cents of the target, the needle turns green and a check mark appears next to the cents reading.

The last reading stays visible after the sound fades. Pluck a different string to switch the target. Tap a string in the row at the bottom to lock the target manually; tap it again to return to automatic selection.

The moving green wave shows microphone input level. The settings icon opens A4 calibration, vibration, screen-on, and theme controls. Available tunings are Standard, Drop D, Half-step down, Open G, and DADGAD.

## Privacy

Audio is processed on the phone while the tuner screen is visible. Tune does not save recordings or send audio over the network. See the [privacy policy](docs/PRIVACY.md).

## License

Tune is available under the [MIT License](LICENSE).
