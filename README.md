
# 构建

```PowerShell
.\gradlew.bat :app:assembleDebug
```

```shell
./gradlew :app:assembleDebug
```

# 安装 APK

```shell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```