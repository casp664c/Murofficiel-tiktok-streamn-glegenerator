# TikTok-integration — uprøvet

Projektets mål er en selvudviklet uofficiel Android-generator, som kan skaffe reelle streamingoplysninger for en konto, der er autoriseret til LIVE. **Der er ingen verificeret implementering af dette mål i v0.1**.

TikTok kontrollerer selv, om en LIVE-session må oprettes, og udsteder/accepterer de gyldige oplysninger. En tilfældigt genereret nøgle vil ikke blive accepteret, og en Altare-server ændrer ikke dette.

## Verificeringsplan
1. Dokumentér tekniske adgangskrav og betingelser til TikTok LIVE.
2. Brug en konto, som ejeren har lov til at bruge, og anvend kun brugerautoriserede sessioner.
3. Adskil session-provider fra Android-UI og Node.js-health API.
4. Anvend ikke uverificerede signeringstjenester eller indsamling af adgangskoder til tredjepart.
5. Test eventuel senere nøgleudstedelse, udløbstid og RTMP/RTMPS-forbindelse uden at offentliggøre credentials.
6. Lad systemet returnere en præcis fejl, hvis TikTok afviser en session.

Ingen kode i v0.1 må fremstille et fiktivt resultat som en fungerende TikTok-nøgle.
