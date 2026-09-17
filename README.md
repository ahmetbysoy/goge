# Kalkan Klavye (goge)

Ultra hafif, saf native hızında Türkçe klavye uygulaması.

## GitHub Actions — otomatik APK

`main` / `master` branch'ine her push'ta (veya manuel **Run workflow**) APK üretilir:

- Workflow: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)
- Çıktı: **Actions → ilgili run → Artifacts** altında `kalkan-klavye-debug`
- `main` push'larında ayrıca **Releases** sekmesine de yüklenir

### Manuel çalıştırma

1. Repo → **Actions** → **Build APK**
2. **Run workflow**
3. `debug` veya `release` seç

### Release imzalama (opsiyonel)

Release APK'yı kendi keystore'unla imzalamak için repo **Settings → Secrets and variables → Actions** altına ekle:

| Secret | Açıklama |
| --- | --- |
| `KEYSTORE_BASE64` | `.jks` / `.keystore` dosyasının base64 hali (`base64 -w0 my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore şifresi |
| `KEY_PASSWORD` | Key şifresi |
| `KEY_ALIAS` | Alias (yoksa `upload`) |

Secret yoksa release de debug keystore ile imzalanır (test için yeterli).

### Yerel derleme

```bash
chmod +x ./gradlew
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```
