# Google Drive yedekleme kurulumu (tek seferlik)

Kullanıcı tarafı basit: uygulamada **Google ile Giriş Yap**.
Geliştirici tarafında Google Cloud'da bir kere OAuth client tanımlaman lazım.

## 1) Google Cloud proje

1. https://console.cloud.google.com/
2. Yeni proje: **Kalkan Klavye**
3. **APIs & Services → Library** → **Google Drive API** → Enable

## 2) OAuth consent screen

1. APIs & Services → **OAuth consent screen**
2. External (veya Internal)
3. App name: Kalkan Klavye
4. Scopes ekle: `https://www.googleapis.com/auth/drive.file`
5. Test users: kendi Gmail'ini ekle (External + Testing ise)

## 3) Android OAuth client

1. Credentials → **Create credentials → OAuth client ID**
2. Application type: **Android**
3. Package name: `com.aistudio.kalkankeyboard.core`
4. SHA-1 (bu repodaki debug.keystore):

```
24:81:CF:31:5C:8E:53:27:33:13:6A:6C:C5:2E:6D:62:E5:E1:22:E6
```

SHA-256 (istersen):

```
EA:F9:9A:CB:62:E5:F1:55:64:6F:DF:66:39:76:D9:98:24:28:3A:EE:CA:70:D5:B4:DF:30:74:E0:D1:98:ED:D9
```

5. Create

> Play Store release için release keystore SHA-1'ini de ayrı Android client olarak ekle.

## 4) Telefonda kullanım

1. Kalkan Klavye → **Ayarlar**
2. **Google ile Giriş Yap** → hesabını seç → Drive iznini onayla
3. **Yedekle** → Drive'da `KalkanKlavye/clipboard_master.json` oluşur
4. Başka telefonda aynı hesapla giriş → **Geri Yükle**

## Neden Sheets değil?

Sheets hücre boyutu / satır limiti sınırsız panoyu kırar.
Drive dosyası = sınırsız JSON, merge/restore net.
