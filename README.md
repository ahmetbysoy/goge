# Kalkan Klavye (goge)

Ultra hafif, saf native hızında Türkçe klavye uygulaması.

## Boyut hedefi

APK **~2–4 MB** aralığında tutulur. Bunun için:

- Firebase / Room / Retrofit / Moshi / Material Icons Extended **kullanılmıyor**
- R8 minify + resource shrink **açık** (debug & release)
- Yalnızca `armeabi-v7a` + `arm64-v8a` ABI’leri paketlenir

## GitHub Actions — otomatik APK

`main` push / PR / manuel **Run workflow** ile APK üretilir.

- Workflow: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)
- **Actions → Artifacts** → `kalkan-klavye-debug` / `kalkan-klavye-release`
- `main` push’larında **Releases** altına da yüklenir

### Yerel derleme

```bash
chmod +x ./gradlew
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/
```
