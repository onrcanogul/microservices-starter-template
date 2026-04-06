# Cache Starter

### Amaç
**Cache Starter**, Redis destekli Spring Cache entegrasyonunu tek bir bağımlılıkla sağlar. JSON serializasyon, yapılandırılabilir TTL ve key prefix izolasyonu ile gelir.

---

### Nasıl Çalışır
1. **RedisCacheManager** — JSON serializasyon ile Redis-backed `CacheManager` bean'i oluşturulur.
2. **Global TTL** — Tüm cache'lere uygulanan varsayılan yaşam süresi (`acme.cache.default-ttl`).
3. **Per-cache TTL** — Cache bazında farklı TTL değerleri (`acme.cache.ttl-overrides.<name>=<duration>`).
4. **Key Prefix** — Paylaşılan Redis instance'larında namespace izolasyonu.
5. **Transaction-aware** — Spring `@Transactional` ile uyumlu (commit sonrası cache güncelleme).

---

### Konfigürasyon

| Property | Varsayılan | Açıklama |
|----------|-----------|----------|
| `acme.cache.enabled` | `true` | Cache'i tamamen devre dışı bırak |
| `acme.cache.default-ttl` | `10m` | Tüm cache'ler için varsayılan TTL |
| `acme.cache.ttl-overrides.<name>` | — | Cache bazında TTL geçersiz kılma |
| `acme.cache.key-prefix` | `cache:` | Redis key prefix'i |
| `acme.cache.use-key-prefix` | `true` | Key prefix kullanımını aç/kapat |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |

---

### Kullanım

**1. Bağımlılık ekle:**
```xml
<dependency>
    <groupId>com.acme.enterprise</groupId>
    <artifactId>cache-starter</artifactId>
</dependency>
```

**2. Metotlara cache anotasyonu ekle:**
```java
@Cacheable(value = "users", key = "#userId")
public UserDto findById(Long userId) {
    return userRepository.findById(userId)
            .map(this::toDto)
            .orElseThrow();
}

@CacheEvict(value = "users", key = "#userId")
public void update(Long userId, UpdateUserRequest req) {
    // ...
}

@CacheEvict(value = "users", allEntries = true)
public void clearUserCache() {
    // tüm user cache'ini temizle
}
```

**3. (Opsiyonel) Per-cache TTL geçersiz kılma:**
```yaml
acme:
  cache:
    default-ttl: 10m
    ttl-overrides:
      users: 30m       # user cache 30 dakika
      products: 5m     # product cache 5 dakika
```
