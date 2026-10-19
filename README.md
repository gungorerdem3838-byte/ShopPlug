# SimpleShop

Paper **1.21.11** için GUI tabanlı market eklentisi.

- **Vault otomatik tanınır.** Sunucuda Vault + bir ekonomi eklentisi (EssentialsX, CMI vb.) varsa ek ayar gerekmez.
- **Ek para birimi sistemi.** Vault'tan bağımsız, istediğin kadar para birimi (kristal, token, puan...) tanımlanır.
- Varsayılan `shop.yml` (SimpleShop - Zor Ekonomi v4): 10 kategori, 226 eşya, spawner sekmesi.
- Alt kategoriler, sayfalama, yetki kontrolü, komut eşyaları (spawner vb.).

## Derleme

Gereken: JDK 21 + Maven.

```bash
mvn clean package
```

Jar: `target/SimpleShop-1.0.0.jar`

### GitHub'da otomatik derleme

Repoya push et; `.github/workflows/build.yml` her push / PR'da derler.
Jar'ı **Actions → son çalışma → Artifacts → SimpleShop** altından indirirsin.
`v1.0.0` gibi bir etiket (tag) push edersen jar otomatik olarak **Releases** sayfasına da eklenir.

```bash
git init -b main
git add .
git commit -m "SimpleShop ilk surum"
git remote add origin https://github.com/KULLANICI/SimpleShop.git
git push -u origin main
```

## Kurulum

1. Jar'ı `plugins/` klasörüne at (Vault ve ekonomi eklentin zaten orada olmalı).
2. Sunucuyu başlat. `plugins/SimpleShop/` altında `config.yml`, `messages.yml`, `shop.yml` oluşur.
3. Konsolda para birimi durumu yazılır, ör. `Para birimi 'vault' (varsayılan): Vault ekonomisi: EssentialsX`.

## Komutlar

| Komut | Açıklama | Yetki |
|---|---|---|
| `/shop [kategori]` (`/market`) | Marketi açar | `simpleshop.use` |
| `/sell hand` / `/sell all` | Eldekini / envanterdeki satılabilir her şeyi satar | `simpleshop.sell` |
| `/currency [balance\|list]` | Bakiyeleri gösterir | `simpleshop.currency` |
| `/currency pay <oyuncu> <birim> <miktar>` | Para gönderir | `simpleshop.currency.pay` |
| `/currency give\|take\|set <oyuncu> <birim> <miktar>` | Yönetici bakiye işlemleri | `simpleshop.currency.admin` |
| `/simpleshop reload` | Tüm dosyaları yeniden yükler | `simpleshop.admin` |

Menüde: **Sol tık** 1 al · **Shift+Sol** bir stack al · **Sağ tık** 1 sat · **Shift+Sağ** envanterdeki hepsini sat.
Sadece düz eşyalar satılır (büyülü, isimli, hasarlı olanlar satılmaz).

## Para birimleri (`config.yml`)

```yaml
default-currency: vault
currencies:
  vault:                # Vault'u otomatik tanır
    type: VAULT
    symbol: "$"
    format: "{symbol}{amount}"
  kristal:              # ek para birimi, bakiyeler data/balances.yml'de
    type: INTERNAL
    name: "Kristal"
    format: "&b{amount} Kristal"
    decimals: 0
```

İstediğin kadar `INTERNAL` birim ekleyebilirsin. Vault yüklü değilse `vault` birimi "kullanılamıyor" olur, eklenti çalışmaya devam eder ve diğer birimler kullanılabilir.

### shop.yml'de para birimi seçmek

Para birimi yazmayan eşyalar `default-currency` ile işlem görür. Kategoriye veya eşyaya `currency:` ekleyerek değiştirebilirsin:

```yaml
spawners:
  currency: kristal          # bu kategorideki her şey Kristal ile
  items:
    '0': { material: DIAMOND, name: '&bElmas', buy-price: 50, sell-price: 10 }
```

Komut çalıştıran eşya (ör. spawner) örneği:

```yaml
'0':
  material: SPAWNER
  name: '&7&lZombie Spawner'
  lore: ['&7Fiyat: &b100 kristal']
  buy-price: 100          # Kristal bakiyesinden düşer (currency: kristal ile)
  currency: kristal
  command: 'spawner_satin_al zombie %player%'
  run-as: console         # console | player
  close-on-click: false
```

`buy-price` vermezsen eşya ücretsiz tetiklenir; ücret kesimini komutun kendisi (ör. Skript) yapar. Varsayılan `shop.yml` bu şekildedir, yani mevcut `kristal.sk` düzenin aynen çalışır. Komut başarısız olursa kesilen ücret iade edilir.

## shop.yml alanları

`material`, `name`, `lore`, `buy-price`, `sell-price`, `currency`, `command` / `commands`, `run-as`, `permission`, `close-on-click`.
Kategori: `name`, `description`, `icon`, `slot`, `permission`, `currency`, `items`, `subcategories`.
Tanınmayan materyaller konsola uyarı yazılıp atlanır (sunucu sürümünde olmayan eşyalar yüzünden eklenti bozulmaz).
