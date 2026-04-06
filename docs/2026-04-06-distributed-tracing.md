# Dağıtık İzleme (Distributed Tracing) Geliştirmesi — Tasarım Kararı

**Tarih:** 2026-04-06  
**Modüller:** `starters/observability-starter`, `services/example-service`, `infra/docker`, `infra/grafana`  
**Durum:** Uygulandı

---

## Ne Yapıldı

### 1. Uçtan Uca Dağıtık İzleme Bağlantısı
- `example-service`'e `observability-starter` bağımlılığı eklendi. Daha önce servisin classpath'inde tracing bridge (`micrometer-tracing-bridge-otel`) veya OTLP exporter bulunmadığından, konfigürasyon mevcut olmasına rağmen trace verisi hiçbir zaman dışa aktarılmıyordu.

### 2. Starter'da OTLP Endpoint Varsayılanı
- `observability-starter/application.yml`'e `management.otlp.tracing.endpoint` eklendi, varsayılan değer: `http://localhost:4318/v1/traces` (OTLP HTTP protokolü).
- Endpoint, `OTEL_EXPORTER_OTLP_ENDPOINT` ortam değişkeni ile geçersiz kılınabilir — docker-compose'da otomatik olarak Jaeger'a yönlendirilir.

### 3. Bozuk `tracingPropsBridge` Bean'i Kaldırıldı
- `ObservabilityAutoConfiguration`'daki `tracingPropsBridge` metodu, mükerrer bir `ObservationProperties` bean'i oluşturuyordu:
  - `acme.obs.tracing.probability` değerini Spring Boot'un sampling konfigürasyonuna hiçbir zaman bağlamıyordu
  - Spring Boot'un kendi `ObservationProperties` kaydıyla çakışabiliyordu
- Kaldırıldı. Örnekleme oranı standart `management.tracing.sampling.probability` property'si ile yapılandırılır.

### 4. Grafana Datasource Provisioning
- `infra/grafana/provisioning/datasources/datasources.yml` oluşturuldu, otomatik olarak şunları yapılandırır:
  - **Prometheus** — varsayılan metrik veri kaynağı
  - **Jaeger** — tracing veri kaynağı
- Grafana, her iki veri kaynağı önceden yapılandırılmış olarak başlar — manuel kurulum gerekmez.

### 5. Docker Compose Güncellemeleri
- `example-service` konteynerine `jaeger:4318`'e yönlenen `OTEL_EXPORTER_OTLP_ENDPOINT` ortam değişkeni eklendi
- Grafana provisioning dizini Grafana konteynerine mount edildi
- Başlatma sırası için Jaeger, Grafana'nın bağımlılığı olarak eklendi

---

## Neden Yapıldı

Dağıtık izleme, mikroservisler arasındaki gecikme sorunlarını debug etmek için **tek yol**dur. Olmadan:

- 5 servise dokunan ve 3 saniye süren bir istek, hangi servisin yavaş olduğuna dair hiçbir görünürlük sağlamaz
- Servisler arasındaki aralıklı hatalar yeniden üretilmesi ve teşhis edilmesi neredeyse imkansız hale gelir
- Kullanıcının isteği ile tetiklediği downstream Kafka event'leri arasındaki korelasyon görünmezdir

Yüksek trafikte (10k+ RPS), tracing şunlar için kritiktir:
- Sıcak yolları (hot path) ve kuyruk gecikme (tail-latency) kaynaklarını belirleme
- Bir servis bozulduğunda kademeli etkileri tespit etme
- Gerçek istek akış pattern'lerine dayalı kapasite planlaması

---

## Değerlendirilen Alternatifler

### Tracing Backend

| Seçenek | Artılar | Eksiler | Karar |
|---------|---------|---------|-------|
| **Jaeger (seçilen)** | Docker-compose'da zaten var, OTLP-native, olgun, iyi Grafana entegrasyonu | Ölçekte özel depolama gerektirir (Cassandra/Elasticsearch) | ✅ Dev ve orta ölçek prod için en uygun |
| **Grafana Tempo** | Maliyet etkin (object storage backend), derin Grafana entegrasyonu | Daha yeni, sorgu yetenekleri daha az denenmiş | İyi alternatif — aynı OTLP endpoint arkasında daha sonra geçiş yapılabilir |
| **Zipkin** | Basit, uzun geçmişi var | Native OTLP desteği yok (collector bridge gerektirir), daha az özellik | Reddedildi — OTLP endüstri standardı |
| **AWS X-Ray / GCP Cloud Trace** | Yönetilen hizmet, altyapı yükü yok | Vendor lock-in, özel SDK entegrasyonu gerektirir | Bulut dağıtımları için tamamlayıcı |

### İzleme Protokolü

| Seçenek | Artılar | Eksiler | Karar |
|---------|---------|---------|-------|
| **OTLP HTTP (seçilen)** | Spring Boot native desteği, daha basit firewall kuralları | gRPC'den biraz daha fazla ek yük | ✅ Varsayılan |
| **OTLP gRPC** | Yüksek hacimli tracing için daha verimli | HTTP/2 gerektirir, daha karmaşık proxy konfigürasyonu | `management.otlp.tracing.transport=grpc` ile kullanılabilir |
| **Zipkin B3** | Geniş legacy desteği | Standart dışı, daha az backend native olarak destekler | Reddedildi |

### Örnekleme (Sampling) Stratejisi

| Strateji | Olasılık | Kullanım Alanı |
|----------|----------|----------------|
| Dev/lokal | `1.0` (%100) | Debug için her trace'i gör |
| Starter varsayılanı | `0.10` (%10) | Production temel — yönetilebilir hacim |
| Yüksek trafik prod | `0.01` (%1) | Depolamayı aşmadan 100k+ RPS'ye ölçeklen |

Servisler `management.tracing.sampling.probability` veya `TRACING_SAMPLING_PROBABILITY` ortam değişkeni ile geçersiz kılar.

---

## Nasıl Ölçeklenir

| Ölçek | Konfigürasyon |
|-------|---------------|
| **Lokal geliştirme** | Jaeger all-in-one (in-memory), %100 örnekleme |
| **Küçük prod (< 1k RPS)** | Jaeger all-in-one + Badger depolama, %10 örnekleme |
| **Orta prod (1k–50k RPS)** | Jaeger + Elasticsearch/Cassandra backend, %5–10 örnekleme, tamponlama için OTel Collector |
| **Büyük prod (50k+ RPS)** | Grafana Tempo + S3/GCS backend (maliyet etkin), %1 örnekleme, OTel Collector ile kuyruk-tabanlı (tail-based) örnekleme |

OTLP endpoint soyutlaması sayesinde backend, uygulama kodunda hiçbir değişiklik yapılmadan değiştirilebilir (Jaeger → Tempo → vendor) — yalnızca `OTEL_EXPORTER_OTLP_ENDPOINT` ortam değişkeni değişir.

---

## Trace Akışı

```
Kullanıcı İsteği → API Gateway (span 1)
    → example-service (span 2, span 1'in alt span'ı)
        → PostgreSQL sorgusu (span 3)
        → Outbox ile Kafka publish (span 4)
            → consumer-service Kafka consume (span 5)
```

Tüm span'lar aynı `traceId`'yi paylaşır, şuralarda görünür:
- **Yanıt başlığı:** `Trace-Id: <64-hex-karakter>` (observability-starter filter'ından)
- **Loglar:** `[trace=<traceId> span=<spanId>]` (logging pattern'inden)
- **Jaeger UI:** `http://localhost:16686`
- **Grafana:** Explore → Jaeger veri kaynağı
