# QR Wallet (Android)

This is a minimal Kotlin + Jetpack Compose Android app that stores QR codes (bonus/loyalty cards) locally on the device using Room.

Features
- Scan QR codes using camera (ZXing)
- Import image and decode QR from photo
- Store cards locally (Room)
- View and delete stored cards

Open the `d:/Repositories/CardiganStore` directory in Android Studio and build the project.

Run notes
- Requires Android Studio (Arctic Fox or newer) and Android SDK
- Grant camera and storage permissions when the app requests them

Build from command line (Windows PowerShell):

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run the `app` configuration.

What's included
- Camera scanner (continuous) with flashlight toggle
- Import image from gallery and decode QR from photo
- Local storage with Room, optional saved images in internal storage
- View, edit title, and delete cards

Next steps
- Polish UI and theme
- Add backup/restore (local file) if you want to migrate between phones
- Add export to image or share feature

Backup/Restore
- Use the top-right "Backup" action to export a ZIP containing `cards.json` and saved images.
- Use the "Restore" action to import a backup ZIP; the app will restore cards and images locally.
