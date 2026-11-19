# LyricSync Çeviri — Gerçek Zamanlı Şarkı Sözü & Çeviri Uygulaması

Spotify ve diğer müzik uygulamalarından çalan şarkıları anlık olarak takip eden, LRCLIB üzerinden senkronize (`.lrc`) sözleri çeken ve tüm şarkıyı bağlamıyla anında çeviren Android (Kotlin + Jetpack Compose) uygulaması.

---

## 📱 APK İndir (Telefon İçin Tek Tık)

> **[⬇️ Son Sürüm APK İndir (LyricSync.apk)](https://github.com/MamiZ35/LyricSync/releases/latest/download/LyricSync.apk)**
>
> Tüm sürümleri görmek için: **[Releases Sayfası](https://github.com/MamiZ35/LyricSync/releases/latest)**

*(Tüm sürümler aynı imza anahtarıyla ve artan sürüm numarasıyla derlendiği için yeni güncellemeleri eskisini silmeden doğrudan üzerine kurabilirsiniz.)*

---

## ✨ Özellikler
- **Canlı Medya Takibi:** `NotificationListenerService` ve `MediaSessionManager` ile şarkı adı, sanatçı, albüm kapağı, toplam süre ve anlık saniye takibi.
- **Bağlamsal Bulut Çevirisi + Çevrimdışı ML Kit Yedeği:** Şarkıyı satır satır parçalamak yerine tam bağlamıyla ~0.3 sn'de çevirir; internet yokken argo-düzeltilmiş cihaz içi Google ML Kit'e düşer.
- **Yüzen Altyazı Penceresi (Floating Overlay):** Spotify üstünde çalışan, sürüklenebilir, opaklığı ve yazı boyutu ayarlanabilen, tek dokunuşla kilitlenebilir (click-through) mini pencere.
- **Akıllı Liste & Kontroller:**
  - Çalan satıra otomatik odaklanma ve elle kaydırınca 4.5 sn duraklayıp **"🎯 Aktif Satıra Dön"** butonu gösterme.
  - Satıra **kısa dokununca** şarkıyı o saniyeye sarma, **uzun basınca** kopyalama ve paylaşma.
  - Uygulama içi tek tıkla güncelleme kontrolü ve manuel şarkı sözü arama.

---

## 🔒 Android 13+ Kurulum Notu
APK olarak kurduktan sonra Bildirim Erişimi'nde *"Güvenliğiniz için bu ayar şu anda kullanılamıyor"* uyarısı görürseniz:
1. Uygulama içindeki **1.A: Uygulama Bilgilerini Aç** butonuna basın.
2. Sağ üst **⋮** menüsünden **"Kısıtlanmış ayarlara izin ver"** seçeneğini onaylayın.
3. Geri dönüp **1.B: Bildirim Erişimini Aç** adımından izni verin.
