# Building Android

Use the committed wrapper and JDK 17:

```powershell
$env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-17"
$env:Path = "$env:JAVA_HOME\\bin;$env:Path"
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
```

Before native runtime validation, build artifacts in `wellfriend-perception`, run
`scripts/sync-android-abi.ps1` from this repository, then validate with
`npm run verify:runtime-artifacts`. A device/emulator smoke requires an installed
SDK image and uses `./gradlew connectedDebugAndroidTest`.
