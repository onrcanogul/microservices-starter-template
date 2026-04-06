# Resilience4j Starter — Tasarım Kararı

**Tarih:** 2026-04-06
**Modül:** `starters/resilience-starter`
**Durum:** Uygulandı

---

## Ne Yapıldı

Yeni bir `resilience-starter` auto-configuration modülü oluşturuldu:

1. **Bağımlılık birleştirme** — `resilience4j-spring-boot3`, `resilience4j-micrometer` ve `spring-boot-starter-aop` tek bir starter dependency ile projede kullanılabilir hale getirildi.
2. **Kurumsal varsayılanlar** — CircuitBreaker, Retry, Bulkhead ve TimeLimiter için production-ready varsayılan değerler starter'ın `application.yml` dosyasında tanımlandı.
3. **Yapılandırılmış loglama** — CircuitBreaker durum geçişlerini ve retry denemelerini SLF4J'ye loglayan `RegistryEventConsumer` bean'leri eklendi. `acme.resilience.*` property'leri ile kontrol edilebilir.
4. **Metrik entegrasyonu** — `resilience4j-micrometer` sayesinde tüm resilience pattern'leri otomatik olarak Prometheus/Grafana'ya metrik gönderir.

---

## Neden Yapıldı

Resilience pattern'leri olmadan, tek bir yavaş veya hatalı downstream servis tüm sistemi çökertebilir:

- **Circuit Breaker yok** → thread'ler ölü servisi bekleyerek birikir → thread pool tükenir → tüm endpoint'ler yanıt veremez hale gelir.
- **Retry yok** → geçici ağ sorunları (DNS gecikmeleri, TCP reset'leri) kullanıcıya gereksiz hatalar olarak yansır.
- **Bulkhead yok** → sorunlu bir entegrasyon tüm kullanılabilir thread'leri ele geçirir, sağlıklı endpoint'ler aç kalır.
- **TimeLimiter yok** → takılmış bir bağlantı bir thread'i sonsuza kadar bloklar.

Yüksek trafikte (10k+ RPS), bu arıza modları saniyeler içinde birbirini tetikler. resilience-starter, dependency eklendiğinde otomatik olarak devreye giren bir güvenlik ağı sağlar.

---

## Değerlendirilen Alternatifler

### 1. Spring Cloud Circuit Breaker Abstraction
- **Artıları:** Vendor-neutral API, Spring Cloud ekosistemiyle uyumlu.
- **Eksileri:** Yalnızca CircuitBreaker destekler. Retry, Bulkhead, RateLimiter veya TimeLimiter yok. Tüm resilience pattern'lerine ihtiyaç duyan bir mikroservis şablonu için çok kısıtlı.
- **Karar:** Reddedildi — Resilience4j'nin tam özellik setine ihtiyacımız var.

### 2. Sentinel (Alibaba)
- **Artıları:** Flow control, degradation, sistem yükü koruması.
- **Eksileri:** Daha ağır operasyonel yük (dashboard server gerekli). Spring ekosistemine Resilience4j'ye kıyasla daha az topluluk desteği. Dökümantasyon ağırlıklı olarak Çince.
- **Karar:** Reddedildi — stack'imiz için daha yüksek operasyonel maliyet, daha düşük topluluk desteği.

### 3. Manuel Implementasyon (custom interceptor'lar)
- **Artıları:** Tam kontrol.
- **Eksileri:** Resilience4j'nin kayan pencere (sliding window), half-open mantığı, retry stratejileri, actuator health indicator'ları ve Micrometer entegrasyonunu eşleştirmek için devasa mühendislik çabası. Topluluk bakımı yok.
- **Karar:** Reddedildi — tekerleği yeniden icat etmek.

### 4. Istio / Service Mesh Retry + Circuit Breaking
- **Artıları:** Dil-bağımsız, altyapı seviyesinde.
- **Eksileri:** Service mesh (Istio/Linkerd) dağıtımı gerektirir. Her hop'ta gecikme ek yükü ekler. Mesh seviyesinde iş hataları ile geçici hatalar arasında ayrım yapılamaz. K8s dışı ortamlarda (lokal geliştirme, bare metal) kullanılamaz.
- **Karar:** Tamamlayıcı, alternatif değil. Uygulama seviyesi resilience daha ince taneli, kod-farkındalıklı kontrol sağlar. Mesh seviyesi retry'larla ikinci katman olarak birleştirilebilir.

---

## Varsayılan Değerlerin Gerekçesi

| Parametre | Değer | Gerekçe |
|-----------|-------|---------|
| `failure-rate-threshold` | %50 | Tutucu — aralıklı hatalardan kaynaklanan erken devre açılmasını önler |
| `sliding-window-size` | 10 | Hızlı tepki verecek kadar küçük, gürültüden etkilenmeyecek kadar büyük |
| `wait-duration-in-open-state` | 10s | Downstream servise toparlanma süresi tanır, çok uzun bekletmez |
| `retry.max-attempts` | 3 | Tipik geçici hataları kapsar; daha fazla deneme kabul edilemez gecikme ekler |
| `retry.exponential-backoff` | 1s → 2s → 4s | Kısmi kesintilerde thundering herd'ü önler |
| `bulkhead.max-concurrent-calls` | 25 | Thread pool'ları korurken makul paralelliğe izin verir |
| `timelimiter.timeout-duration` | 3s | Çoğu mikroservis çağrısının P99 gecikmesi 2s'den az olmalıdır |

---

## Nasıl Ölçeklenir

| Ölçek | Davranış |
|-------|----------|
| **Küçük (< 100 RPS)** | Varsayılanlar kutudan çıktığı gibi çalışır. Hatalar nadirdir; circuit breaker nadiren aktif olur. |
| **Orta (100–10k RPS)** | Exponential backoff, sorunlu bağımlılıklarda thundering herd'ü önler. Bulkhead yavaş entegrasyonları izole eder. |
| **Büyük (10k–100k+ RPS)** | Circuit breaker kritik öneme sahiptir — olmadan tek bir bozulmuş servis tüm cluster'da kademeli çöküşe neden olur. Operatörler, gözlemlenen P99 gecikmeye göre instance başına eşik değerleri ayarlar. |
| **Servis bazlı ayarlama** | Servisler, SLA gereksinimlerine göre global varsayılanları geçersiz kılmak için instance'a özgü konfigürasyonlar tanımlar (`resilience4j.circuitbreaker.instances.<isim>`). |

---

## Kullanım

Herhangi bir servisin `pom.xml`'ine ekleyin:
```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>resilience-starter</artifactId>
</dependency>
```

Servis metodlarını işaretleyin:
```java
@CircuitBreaker(name = "inventory", fallbackMethod = "inventoryFallback")
@Retry(name = "inventory")
public InventoryResponse checkStock(String sku) {
    return inventoryClient.check(sku);
}
```
