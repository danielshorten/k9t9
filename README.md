# k9t9
Minimalist, Nokia-inspired T9 keyboard for Android.

### Features
- Predictive text entry for English language
- Barebones punctuation (through the 1 key)
- 4 entry modes (cycled using pound key)
  - Lowercase word
  - Capitalized word
  - All caps word
  - Numbers
- Recomposing previously-entered words

### Installation
Install via ADB:
```
adb install <release>.apk
```
Set K9T9 as the active IME:
```
adb shell ime set com.shortendesign.k9keyboard/.K9InputMethodServiceImpl
```
On first run after install, K9T9 may take some time to load the word database and populate its
data structures.  During this time, the word input modes will not work.

### Override Settings
1. Save a copy of `k9t9.properties-example`.
```
cp k9t9.properties-example ~/k9t9.properties
```

2. Update settings, such as key code mappings.

    For example, to map Android key code `4` to the `DELETE` function:
    ```
    key.DELETE=4
    ```
3. Copy the updated settings file to your device using `adb`
```
adb push ~/k9t9.properties /storage/emulated/0/Android/data/com.shortendesign.k9keyboard/files/k9t9.properties
```
You will need to restart the IME service for the settings to take effect (restarting your device may
be the simplest way).

To remove the custom settings file:
```
adb shell rm /storage/emulated/0/Android/data/com.shortendesign.k9keyboard/files/k9t9.properties
```

### Philosophy & Limitations
This IME was initially designed to replace an inferior predictive text engine on a basic Android
feature phone.  This phone (and I'm assuming others like it) do not run the Google Play store or
services, and doesn't even support a UI for selecting a different IME, even if it were installed.

For this reason, I don't have plans to publish this app to the Google Play store.  It is intended
for those who know and care enough about such Android feature phones to use the Android SDK to set
it up manually.

I have also not included any settings UI for the IME.

### Removal
```
adb uninstall com.shortendesign.k9keyboard
```
