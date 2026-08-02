# Fonts

Not yet added. `Font.custom` silently falls back to the system font when a
family is missing, so the app builds and runs without these — it just renders
off-brand.

Required files, matching the substitutions the Android app uses (Poppins for
Gilroy, Alex Brush for Alexander Lettering — both SIL Open Font License):

- `Poppins-Regular.ttf`
- `Poppins-SemiBold.ttf`
- `Poppins-ExtraBold.ttf`
- `AlexBrush-Regular.ttf`

Drop them in this folder, then add to **both** `Info-Debug.plist` and
`Info-Release.plist`:

```xml
<key>UIAppFonts</key>
<array>
    <string>Poppins-Regular.ttf</string>
    <string>Poppins-SemiBold.ttf</string>
    <string>Poppins-ExtraBold.ttf</string>
    <string>AlexBrush-Regular.ttf</string>
</array>
```

The PostScript name inside the file is what `Font.custom` matches, and it does
not always equal the filename. Verify with:

```sh
python3 -c "import sys;from fontTools.ttLib import TTFont;f=TTFont(sys.argv[1]);print([r.toUnicode() for r in f['name'].names if r.nameID==6])" Poppins-ExtraBold.ttf
```

If the names differ, update the constants in `UI/Theme/Fonts.swift` — that file
is the only place they appear.

When the licensed Gilroy and Alexander Lettering files arrive, replace these and
change only those same constants. Nothing else references a typeface by name.
