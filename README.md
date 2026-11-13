# Foto-Ematri (SnapPress)

Android-app die foto's maakt met de camera van je telefoon en ze direct
comprimeert naar ongeveer 300 kB met maximale behoud van scherpte.
De foto's worden opgeslagen in de standaard map **Afbeeldingen (Pictures)**
en zijn daarmee direct zichtbaar in je galerij en goed te bekijken op een monitor.

## APK installeren

1. Download [`apk/SnapPress-v1.0-debug.apk`](apk/SnapPress-v1.0-debug.apk) en zet het bestand op je telefoon.
2. Open het bestand op je telefoon en sta "Installeren uit onbekende bronnen" toe.
3. Open SnapPress en tik op **Foto maken**.

De app vraagt geen speciale permissies: de systeemcamera maakt de foto en
de opslag gaat via MediaStore in je eigen Afbeeldingen-map.

## Werking

- Foto wordt op volledige resolutie genomen (bijv. 4032×3024).
- De app zoekt binair naar de hoogste JPEG-kwaliteit die onder de 300 kB blijft.
- Alleen als dat niet lukt (ruisrijke beelden), wordt de afmeting stapsgewijs
  verkleind (factor 0,75, met bilineaire filtering) — minimaal tot 1280 px.
- EXIF-rotatie wordt toegepast zodat de foto altijd rechtop staat.
- Bestandsnaam: `SnapPress_JJJJMMDD_UUmmSS.jpg` in `Pictures`.

## Zelf bouwen

Vereist: JDK 17 en Android SDK (platform 34, build-tools 34.0.0).

```bash
# local.properties aanmaken met: sdk.dir=<pad naar je Android SDK>
gradle assembleDebug
```

De APK verschijnt dan in `app/build/outputs/apk/debug/`.
