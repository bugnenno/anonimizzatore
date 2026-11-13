# Anonimizzatore

App Android che toglie i dati personali da PDF, Word e file di testo prima di condividerli
(per esempio per confrontare offerte di assicurazioni con un assistente AI).

Funziona **senza internet**: il documento non esce mai dal telefono.

## Cosa fa
- Apre PDF, Word (.docx) e testo, anche da "Condividi" / "Apri con" di altre app.
- Sostituisce le parole indicate (nome, indirizzo, città…) con etichette come `[NOME_1]`.
- Riconosce da solo email, telefoni, codice fiscale, IBAN, partita IVA, targhe, numeri di polizza, carte.
- Salva il file anonimo in `Download/Anonimizzatore/` (stesso formato dell'originale, più .txt)
  oppure lo condivide direttamente con un'altra app.
- "Rimetti i dati veri": incolli una risposta con le etichette e torna il testo con i dati reali.

Limiti: il PDF in uscita contiene solo il testo (niente grafica dell'originale);
i PDF scansionati (immagini) non sono supportati.

## Installare
Ogni modifica su `main` fa partire una build su GitHub Actions.
L'APK si scarica da **Releases → ultima versione → Anonimizzatore.apk**.
Android chiede di consentire l'installazione da origini sconosciute.

## Struttura
- `app/src/main/assets/index.html` – tutta la logica (HTML + JavaScript)
- `app/src/main/java/.../MainActivity.java` – WebView, scelta file, salvataggio e condivisione
- `.github/workflows/build-apk.yml` – compila e pubblica l'APK
- `app/release.keystore` – chiave di firma personale (serve per installare gli aggiornamenti sopra la versione precedente)
