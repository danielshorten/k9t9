# k9t9
T9 keyboard for Android

**Features**
- Predictive text entry for English language
- Barebones punctuation (through the 1 key)
- 4 entry modes (cycled using pound key)
  - Lowercase word
  - Capitalized word
  - All caps word
  - Numbers

**Current Limitations**
- No settings UI
- Dictionary is preset—no support for adding or editing words

**Override Settings**
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