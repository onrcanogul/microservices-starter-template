# docs/

Bu klasör, proje kararlarının **neden** alındığını, hangi alternatiflerin düşünüldüğünü ve ölçeklenebilirlik açısından nasıl değerlendirildiğini anlatan dokümantasyon dosyalarını içerir.

## Format

Her dosya şu isimlendirme kuralını takip eder:
```
docs/YYYY-MM-DD-<konu-slug>.md
```

Örnek: `docs/2026-04-06-outbox-pattern-design.md`

## İçerik Yapısı

Her döküman şunları içermelidir:
1. **Ne yapıldı** — Değişikliğin kısa açıklaması
2. **Neden yapıldı** — Motivasyon ve problem tanımı
3. **Değerlendirilen alternatifler** — Neden reddedildi
4. **Ölçeklenebilirlik etkisi** — 100 vs 1M kullanıcı senaryoları

> Bu klasör `.gitignore`'a eklenmiştir, push edilmez. Tamamen kişisel öğrenme amaçlıdır.
