<div align="center">

# 🎵 LyricSync

**Spotify ve medya oynatıcılar için gerçek zamanlı senkronize şarkı sözü, bağlamsal çeviri ve yüzen altyazı uygulaması**

[![Android CI](https://github.com/MamiZ35/LyricSync/actions/workflows/android-build.yml/badge.svg)](https://github.com/MamiZ35/LyricSync/actions/workflows/android-build.yml)
[![Latest Release](https://img.shields.io/github/v/release/MamiZ35/LyricSync?label=Son%20S%C3%BCr%C3%BCm&color=0284C7)](https://github.com/MamiZ35/LyricSync/releases/latest)
[![Download APK](https://img.shields.io/badge/APK%20%C4%B0ndir-LyricSync.apk-10B981?style=flat&logo=android&logoColor=white)](https://github.com/MamiZ35/LyricSync/releases/latest/download/LyricSync.apk)
![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)
![Tech](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)

</div>

---

## 📱 Telefona Kurulum (Tek Tık)

> **[⬇️ Son Sürüm APK İndir (`LyricSync.apk`)](https://github.com/MamiZ35/LyricSync/releases/latest/download/LyricSync.apk)**
>
> 🔗 **[Son Sürüm Sayfasını Aç](https://github.com/MamiZ35/LyricSync/releases/latest)**

- **Sürüm Yükseltme:** Tüm sürümler aynı imza anahtarıyla ve otomatik artan sürüm numarasıyla (`versionCode`) derlenir. Yeni sürümleri eskisini silmeden doğrudan üzerine kurabilir veya uygulama içinden **Ayarlar → 🚀 Güncelleme → İndir & Kur** adımıyla güncelleyebilirsiniz.

---

## ✨ Özellikler

| Modül | Özellikler |
| :--- | :--- |
| **🎧 Canlı Medya Takibi** | `NotificationListenerService` ve `MediaSessionManager` ile Spotify vb. çalarlardan şarkı adı, sanatçı, albüm kapağı, toplam süre ve anlık milisaniye takibi; ana kartta etkileşimli ileri/geri sarma çubuğu ve oynatma kontrolleri. |
| **🌐 Bağlamsal & Çevrimdışı Çeviri** | **Bulut Bağlamsal Motor** tüm şarkıyı tek seferde ~0.3 sn'de çevirir; internet yokken argo düzeltmeli cihaz içi **Google ML Kit** motoruna düşer (Wi-Fi önceliği destekli). |
| **🪟 Yüzen Pencere (Overlay)** | Diğer uygulamaların üstünde çalışan sürüklenebilir mini pencere; canlı önizlemeli boyut/opaklık ayarı, sıradaki satır önizlemesi (`▸`), müzik durunca otomatik gizlenme, hızlı kontroller (`A-`/`⏮`/`⏯`/`⏭`/`A+`) ve kilitle/aç düğmesi. |
| **🌌 Ambiyans Modu** | Albüm kapağından çekilen dominant renk geçişli tam ekran söz okuma modu, satır ortalı otomatik kaydırma, ekranı açık tutma desteği ve tam medya/sarma kontrolleri. |
| **🔁 Öğrenme & Paylaşım Araçları** | Uzun basarak **4 satırlık kıta döngüsü (A–B repeat)**, **PNG görsel söz kartı paylaşımı**, panoya kopyalama, favori yıldızlama ve **Room** çevrimdışı kütüphane. |
| **🎨 Temalar & Sekmeli Ayarlar** | **Koyu**, **Saf Siyah (OLED)**, **Açık** ve **Sistem** temaları; 4 sekmeli düzenli Ayarlar ekranı (`Görünüm`, `Overlay`, `Çeviri`, `Güncelleme`). |

---

## 🔒 Android 13+ Kurulum Notu

APK olarak kurulum sonrası **Bildirim Erişimi** açılırken *"Güvenliğiniz için bu ayar şu anda kullanılamıyor"* uyarısı görürseniz:

1. Uygulama içindeki **1.A: Uygulama Bilgilerini Aç** butonuna dokunun.
2. Sağ üst köşedeki **⋮** menüsünden **"Kısıtlanmış ayarlara izin ver"** seçeneğini onaylayın.
3. Geri dönüp **1.B: Bildirim Erişimini Aç** adımından LyricSync iznini etkinleştirin.

---

## 🏗️ Mimari Özet

```text
app/src/main/java/com/lyricsync/app/
├── domain/        # LrcParser (O(log N) ikili arama), veri modelleri, tema/motor enumları
├── data/
│   ├── local/     # Room önbellek veritabanı (LyricsDatabase) & AppPrefs
│   ├── remote/    # LRCLIB istemcisi, CloudTranslator, UpdateChecker
│   └── repository/# LyricsRepository (çevrimdışı öncelikli akış) & MediaStateBus (StateFlow)
├── service/       # MediaNotificationListenerService & FloatingOverlayService
└── ui/
    ├── screens/   # MainScreen, SettingsDialog, PermissionOnboardingScreen
    ├── theme/     # Material 3 temalar (Dark / AMOLED / Light)
    └── util/      # LyricCardGenerator (1080x1350 PNG kart üretici)
```
