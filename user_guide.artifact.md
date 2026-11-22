# AlpinKey — Kullanım Kılavuzu 🔑

**AlpinKey**, Android telefonunuzu Bluetooth HID profili üzerinden bilgisayarınız için (Windows, macOS, Linux) donanımsal bir **FIDO2 / U2F Güvenlik Anahtarına (Security Key)** dönüştüren güvenlik uygulamasıdır.

İnternet bağlantısına ihtiyaç duymadan, biyometrik doğrulamanızı (Parmak izi / Yüz tanıma / Ekran Kilidi) kullanarak Microsoft, GitHub, Google ve FIDO2 destekli tüm web sitelerine parola kullanmadan veya 2FA (İki Faktörlü Doğrulama) ile güvenli giriş yapmanızı sağlar.

---

## 📌 İçindekiler
1. [Sistem Gereksinimleri](#1-sistem-gereksinimleri)
2. [İlk Kurulum ve Bluetooth Eşleşmesi](#2-i̇lk-kurulum-ve-bluetooth-eşleşmesi)
3. [Giriş Yapma ve Doğrulama Kullanımı](#3-giriş-yapma-ve-doğrulama-kullanımı)
4. [Kimlik Bilgileri (Credentials) Yönetimi](#4-kimlik-bilgileri-credentials-yönetimi)
5. [Şifreli Yedekleme ve Geri Yükle (Backup & Restore)](#5-şifreli-yedekleme-ve-geri-yükle-backup--restore)
6. [FIDO2 Client PIN ve Güvenlik Ayarları](#6-fido2-client-pin-ve-güvenlik-ayarları)
7. [Sıkça Sorulan Sorular ve Sorun Giderme](#7-sıkça-sorulan-sorular-ve-sorun-giderme)

---

## 1. Sistem Gereksinimleri

* **Mobil Cihaz:** Android 9.0 (API Level 28) veya üzeri, Bluetooth ve Ekran Kilidi (Biometric/PIN/Desen) etkin Android telefon.
* **Bilgisayar:** Windows 10/11, macOS veya Linux (Bluetooth HID profili destekli).
* **Tarayıcı:** Google Chrome, Microsoft Edge, Mozilla Firefox veya Apple Safari.

---

## 2. İlk Kurulum ve Bluetooth Eşleşmesi

AlpinKey'i bilgisayarınızla güvenlik anahtarı olarak kullanabilmek için tek seferlik Bluetooth eşleşmesi yapmanız gerekir:

1. **Telefon ve Bilgisayarda Bluetooth'u Açın.**
2. **AlpinKey Uygulamasını Başlatın.**
3. Ana ekranda (`Home` sekmesi) yer alan **ortadaki büyük mor anahtar/güç simgesine** dokunun.
4. Açılan pencerede **`+ Add new device`** seçeneğine dokunun. *(Telefonunuz 60 saniye boyunca bulunabilir moda geçecektir).*
5. **Bilgisayarınızdan:**
   * **Windows:** *Ayarlar > Cihazlar > Bluetooth ve Diğer Cihazlar > Bluetooth Cihazı Ekle* menüsüne gidin.
   * **macOS:** *Sistem Ayarları > Bluetooth* menüsüne gidin.
   * Listed çıkan **AlpinKey** cihazını seçin ve eşleşmeyi onaylayın.
6. Eşleşme tamamlandığında AlpinKey ana ekranında **"Connected to [Bilgisayar Adı]"** ibaresi görünecektir.

---

## 3. Giriş Yapma ve Doğrulama Kullanımı

### Web Sitesinde Giriş Yapma (Örn: Microsoft, GitHub, Google):
1. Bilgisayarınızın tarayıcısından FIDO2 / 2FA destekli bir siteye girin.
2. Giriş yöntemi olarak **"Güvenlik Anahtarı Kullan" (Use a security key)** seçeneğini belirleyin.
3. Tarayıcınız güvenlik anahtarınıza dokunmanızı istediğinde:
   * **Uygulama Açıkken:** Telefon ekranınızda otomatik olarak biyometrik doğrulama / onay penceresi açılır.
   * **Uygulama Arka Plandayken:** Bildirim çubuğunda *"Action Required"* bildirimi belirir. Bildirime dokunduğunuzda uygulama ön plana gelir ve doğrulama penceresini gösterir.
4. Parmak izinizi okutun veya ekran kilidinizi doğrulayın.
5. Bilgisayarınızdaki oturum anında başarıyla açılacaktır! 🎉

---

## 4. Kimlik Bilgileri (Credentials) Yönetimi

Uygulamanın alt menüsündeki **`Credentials`** sekmesinden kayıtlı tüm WebAuthn / FIDO2 anahtarlarınızı görüntüleyebilir ve yönetebilirsiniz:

* **Liste:** Kayıtlı web siteleri (Domain Name), kullanıcı adları ve oluşturulma detayları listelenir.
* **Detay Görüntüleme:** Herhangi bir anahtarın üzerine dokunarak detaylı hesap bilgilerini görebilirsiniz.
* **Anahtar Silme:** Silmek istediğiniz anahtarı listede sola kaydırabilir veya çöp kutusu simgesine dokunarak biyometrik onayınızla güvenli bir şekilde silebilirsiniz.

---

## 5. Şifreli Yedekleme ve Geri Yükle (Backup & Restore)

AlpinKey, kayıtlı anahtarlarınızı kaybetmemeniz için **AES-256-GCM** ve **PBKDF2** ile korunan şifreli yedekleme sistemi sunar.

### 📤 Yedek Alma (Export / Backup):
1. **`Credentials`** sekmesine gidin.
2. Üst kısımdaki **Buluta Yükle / Dışa Aktar (`ic_backup`)** butonuna dokunun.
3. Yedeği korumak için güçlü bir **Parola** belirleyin.
4. Dosya kaydedici açıldığında `.json` uzantılı yedek dosyasını telefonunuza veya Google Drive'a kaydedin.

### 📥 Geri Yükleme (Import / Restore):
1. **`Credentials`** sekmesine gidin.
2. Üst kısımdaki **Buluttan İndir / İçe Aktar (`ic_restore`)** butonuna dokunun.
3. Daha önce kaydettiğiniz `.json` yedek dosyasını seçin.
4. Yedeği oluştururken belirlediğiniz **Parolayı** girin.
5. Anahtarlarınız ve KeyStore sertifikalarınız çözülerek veritabanına eksiksiz bir şekilde yüklenecektir.

---

## 6. FIDO2 Client PIN ve Güvenlik Ayarları

* **FIDO2 Standalone PIN:** `Credentials` sekmesinde yer alan **PIN simgesine** dokunarak FIDO2 doğrulama senaryoları için özel bir Client PIN oluşturabilir veya var olan PIN'i değiştirebilirsiniz.
* **Donanımsal Koruma (Hardware Security):** Cihazınız destekliyorsa anahtarlarınız Android **TEE (Trusted Execution Environment)** veya **StrongBox Keymaster** çip düzeyinde korunur. Üstteki mikroçip simgesine dokunarak cihazınızın donanımsal koruma durumunu inceleyebilirsiniz.

---

## 7. Sıkça Sorulan Sorular ve Sorun Giderme

### ❓ Bilgisayarım AlpinKey'e bağlanamıyor, ne yapmalıyım?
> **Çözüm:** Telefon ve bilgisayarınızdaki Bluetooth'u kapatıp tekrar açın. Ana sayfadaki mor anahtar simgesine dokunup bilgisayarınızı listeden seçerek **"Manual Connect"** yapın.

### ❓ İstek geldiğinde ekranda doğrulama penceresi çıkmıyor?
> **Çözüm:** Android 13/14+ cihazlarda AlpinKey için **Bildirim İzinlerinin** açık olduğundan ve Arka Planda Çalışma kısıtlamasının (Pil tasarrufu) kaldırıldığından emin olun.

### ❓ Telefonumu değiştirirsem anahtarlarıma ne olur?
> **Çözüm:** Yeni cihaza geçmeden önce `Credentials` sekmesinden şifreli **Yedek Alma (Backup)** işlemini yapın. Yeni telefonunuza AlpinKey yükledikten sonra **Geri Yükle (Restore)** seçeneğiyle anahtarlarınızı kolayca aktarabilirsiniz.

---
*Made with ❤ for Security & Freedom.*
