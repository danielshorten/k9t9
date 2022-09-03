# k9t9
T9 keyboard for Android

**Features**
- Predictive text entry for English language
- Basic punctuation (through the 1 key)
- 4 entry modes (cycled using pound key)
  - Lowercase
  - Capitalized word
  - All caps
  - Numbers

**Current Limitations**
- No settings UI
- Hard-coded key behaviour (may not work with your phone)
- Dictionary is preset—no support for adding or editing words

**Override Settings**
1. Save a copy of `k9t9.properties-example`.
```
cp k9t9.properties-example ~/k9t9.properties
```

2. Update settings, such as key code mappings.
3. Copy the updated settings file to your device using `adb`
```
adb push ~/k9t9.properties /storage/emulated/0/Android/data/com.shortendesign.k9keyboard/files/k9t9.properties
```
