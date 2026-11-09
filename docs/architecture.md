# Arkitektur og sikkerhedsgrænser

```text
Android-app (Kotlin / Jetpack Compose)
  - lokal RTMP/RTMPS input-validering
  - kopiering eller manuel deling efter brugerens tryk
  - HTTPS health-check uden hemmeligheder
          |
          | GET /health
          v
Altare/Node.js-backend (ingen deployment endnu)
  - sundhedsstatus og tekniske kapabiliteter
  - POST /api/v1/sessions -> 501, provider er ikke installeret
          |
          X  Ingen fungerende TikTok-integration
```

## Sikkerhedsbeslutninger i v0.1
- Ingen hardcodede kontooplysninger, API-nøgler, adgangstokens eller RTMP-streamnøgler.
- Streamnøglen holdes kun i appens midlertidige UI-hukommelse; den gemmes ikke med rememberSaveable, SharedPreferences eller database.
- Android-backup og skærmbilleder fra appen er slået fra.
- Android kan kun undersøge brugerangivne HTTPS-backends.
- Backend laver ikke eksterne TikTok-kald, logger ikke bodies, og har ikke CORS.
- Deling og kopiering af nøgler er brugerinitieret og kan være følsom. Andre apps/udklipsholder kan beholde hemmelighederne.
- Den lokale RTMP-editor kontrollerer kun format, **ikke** om TikTok har udstedt nøglen eller accepterer LIVE-sessionen.

## Senere faser
- En provider-grænseflade, som kun svarer med virkeligt udstedte, verificerede streamingoplysninger.
- Autentificerede sessionsendpoints med per-bruger tilladelser og tokenstyring.
- Reel forbindelse til en testkonto med tilladelse til TikTok LIVE; mål succes/failure uden at lække nøgler.
- Valgfri kompatibilitet med øvrige streamingapps og streamopsætning.
