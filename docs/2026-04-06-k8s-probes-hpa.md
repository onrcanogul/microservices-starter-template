# Kubernetes Probe'ları, HPA ve Kaynak Limitleri — Tasarım Kararı

**Tarih:** 2026-04-06  
**Modüller:** `infra/k8s/*`, `starters/observability-starter`  
**Durum:** Uygulandı

---

## Ne Yapıldı

### 1. Sağlık Probe'ları (Tüm Servisler)
Her K8s manifest'ine üç katmanlı probe konfigürasyonu eklendi:

- **startupProbe** — `/actuator/health/liveness`'ı cömert bir zaman aşımıyla (150 saniyeye kadar) yoklar. Spring Boot'un yavaş soğuk başlatması sırasında liveness kill'lerini önler.
- **livenessProbe** — Başlatma tamamlandıktan sonra her 10 saniyede `/actuator/health/liveness`'ı kontrol eder. JVM kilitlenirse (deadlock) veya bellek aşarsa (OOM) pod'u yeniden başlatır.
- **readinessProbe** — Her 5 saniyede `/actuator/health/readiness`'ı kontrol eder. Trafik sunulamadığında (DB kapalı, Kafka erişilemez vb.) pod'u Kubernetes Service endpoint'lerinden kaldırır.

`observability-starter`'da Spring Boot Actuator sağlık grupları etkinleştirildi:
```yaml
management:
  endpoint.health.probes.enabled: true
  health.livenessstate.enabled: true
  health.readinessstate.enabled: true
```

### 2. Kaynak İstekleri ve Limitleri (Tüm Servisler)

| Servis | CPU İsteği | CPU Limiti | Bellek İsteği | Bellek Limiti |
|--------|-----------|-----------|---------------|--------------|
| api-gateway | 250m | 500m | 256Mi | 512Mi |
| example-service | 250m | 500m | 256Mi | 512Mi |
| discovery-service | 200m | 400m | 256Mi | 512Mi |
| config-server | 200m | 400m | 256Mi | 384Mi |

### 3. HorizontalPodAutoscaler (Ölçeklenebilir Servisler)
`api-gateway` ve `example-service` için HPA eklendi:
- **Minimum replika:** 2 (yüksek erişilebilirlik temel düzeyi)
- **Maksimum replika:** 10
- **Ölçek büyütme tetikleyicisi:** CPU > %70 VEYA Bellek > %80

Altyapı servisleri (discovery-service, config-server) HPA'ya sahip DEĞİLDİR — tasarım gereği tekil (singleton) çalışırlar.

### 4. Zarif Kapanış (Graceful Shutdown)
`observability-starter` varsayılanlarına eklendi (tüm servislere uygulanır):
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

K8s manifest'leri, uygulama kapanmaya başlamadan önce endpoint kaydının silinmesine izin vermek için `terminationGracePeriodSeconds: 60` ve `sleep 5` içeren bir `preStop` hook'u içerir.

### 5. Yeni Manifest'ler
- `config-server.yaml` — daha önce eksikti
- `example-service.yaml` — daha önce eksikti (iş servisleri için şablon)

---

## Neden Yapıldı

### Probe'lar Olmadan
- K8s, uygulama sağlığı hakkında görünürlüğe sahip değildir → ölü pod'lar trafik almaya devam eder → kullanıcıya 5xx hataları yansır
- Rolling update'ler sırasında yeni pod'lar, Spring context yüklenmeden trafik alır → her deployment'ta hata artışı yaşanır
- Kilitlenmiş (deadlock) JVM'ler hiçbir zaman yeniden başlatılmaz → kalıcı servis bozulması

### Kaynak Limitleri Olmadan
- Tek bir sorunlu servis (bellek sızıntısı, CPU döngüsü) tüm node kaynaklarını tüketir → gürültücü komşu etkisi (noisy neighbor) diğer pod'ları öldürür
- K8s zamanlayıcısı akıllı yerleştirme kararları veremez → dengesiz node kullanımı
- OOM koruması yok → JVM tüm node RAM'ini tüketip kubelet'i çökertebilir

### HPA Olmadan
- Trafik artışları manuel `kubectl scale` gerektirir → yavaş yanıt, insan hatası
- Fazla kapasite tahsisi kaynak israfına yol açar; yetersiz tahsis kesintilere neden olur
- Sürekli yük artışlarından otomatik kurtarma yok

### Zarif Kapanış Olmadan
- Devam eden istekler pod sonlandırması sırasında öldürülür → kullanıcı 502/503 görür
- Veritabanı işlemleri (transaction) yarıda kesilir → veri tutarsızlığı
- Kafka consumer'ları offset commit edemez → yeniden başlatıldığında mesajlar tekrar işlenir

---

## Değerlendirilen Alternatifler

### Probe Endpoint'leri

| Seçenek | Artılar | Eksiler | Karar |
|---------|---------|---------|-------|
| `/actuator/health/liveness` + `/readiness` (seçilen) | Spring Boot native, readiness'ta bağımlılık kontrolü dahil | Actuator bağımlılığı gerektirir | ✅ K8s'te Spring Boot için standart |
| TCP socket probe | HTTP ek yükü yok | Sadece port'un açık olup olmadığını kontrol eder, uygulamanın çalışıp çalışmadığını değil | Reddedildi — yanlış pozitifler |
| Özel `/healthz` endpoint | Tam kontrol | Tekerleği yeniden icat etmek, bağımlılık sağlık entegrasyonu yok | Reddedildi |
| Exec probe (`curl`) | HTTP health olmadan çalışır | Container'da curl gerektirir, daha yavaş, pid kullanır | Reddedildi |

### Otomatik Ölçekleme Stratejisi

| Seçenek | Artılar | Eksiler | Karar |
|---------|---------|---------|-------|
| CPU tabanlı HPA (seçilen) | Basit, güvenilir, kutudan çıktığı gibi çalışır | Gerçek istek yükünü yansıtmayabilir | ✅ İyi varsayılan |
| Özel metrik HPA (ör. RPS) | İstek odaklı servisler için daha doğru | Prometheus Adapter gerektirir, daha karmaşık kurulum | Gelecek iyileştirme (P3) |
| KEDA | Event odaklı ölçekleme, Kafka consumer lag | Ek bileşen dağıtımı ve bakımı | Kafka consumer'ları için gelecek iyileştirme |
| Vertical Pod Autoscaler (VPA) | Kaynak isteklerini otomatik optimize eder | HPA ile çakışır, daha az olgun | Tamamlayıcı — HPA olmayan servisler için kullanılır |

### Kaynak Boyutlandırma

Başlangıç değerleri, JVM varsayılanlarıyla Spring Boot 3.x uygulaması için tutucu temel değerlerdir:
- **256Mi bellek isteği** — Başlangıçta tipik Spring Boot heap + metaspace'i karşılar
- **512Mi bellek limiti** — Trafik artışları ve GC baskısı için OOM kill olmadan tampon alan
- **250m CPU isteği** — Yoğun node'larda bile zamanlama yapılmasını sağlar
- **500m CPU limiti** — Kontrolsüz thread'lerin bir çekirdeği tekelleştirmesini önler

Production ayarlama: Grafana'da `container_memory_usage_bytes` ve `container_cpu_usage_seconds_total` üzerinden gerçek kullanımı izleyin, ardından ayarlayın.

---

## Nasıl Ölçeklenir

| Trafik Seviyesi | Konfigürasyon |
|-----------------|---------------|
| **Dev / < 100 RPS** | HPA min=2 yeterlidir. Kaynak limitleri dev cluster israfını önler. |
| **Orta / 1k RPS** | HPA servis başına 3-5 pod'a ölçeklenir. CPU tabanlı ölçekleme tipik web iş yüklerini karşılar. |
| **Yüksek / 10k+ RPS** | HPA max=10 artırılması gerekebilir. Özel metrikler (Prometheus'tan RPS) düşünülmeli. Pod disruption budget eklenebilir. |
| **Çok Yüksek / 100k+ RPS** | Kafka consumer ölçeklemesi için KEDA'ya geçiş. Node autoscaler (Cluster Autoscaler / Karpenter) eklenmeli. JVM, `-XX:MaxRAMPercentage` ile ayarlanmalı. |

### Rolling Update Sırasında Zarif Kapanış Zaman Çizelgesi

```
t=0s    K8s SIGTERM gönderir + pod'u endpoint'lerden kaldırır
t=0-5s  preStop sleep — kube-proxy/ingress'in endpoint kaldırmasını yaymasına izin verir
t=5s    Spring SIGTERM alır — yeni istek kabul etmeyi durdurur
t=5-35s Spring devam eden istekleri tamamlar (30s zaman aşımı)
t=35s   Uygulama temiz çıkış yapar
t=60s   K8s hâlâ çalışıyorsa zorla öldürür (terminationGracePeriodSeconds)
```

Bu, **sıfır kesintili deployment** sağlar — `kubectl rollout restart` sırasında hiçbir istek düşürülmez.
