# Robot Face

A creepy little AI head, fully on-device APK, brain in the cloud (free).

- **Face:** pixelated canvas face with idle drift, random microexpressions
  (asymmetric blinks, eye saccades, brow twitches, glitch flicker) — no reason,
  just vibes.
- **Ears:** native Android SpeechRecognizer (tap the mic button).
- **Voice:** native Android TextToSpeech, jaw synced to spoken words.
- **Brain:** Google Gemini free API (`gemini-3.5-flash`), called directly from
  the WebView with a fetch — no backend server.

## Get it running (all from your phone)

1. **Create a new GitHub repo** and upload everything in this zip to it
   (GitHub's mobile web upload, or the GitHub app, both work — just drag the
   whole folder structure in, keeping paths intact).
2. Go to the repo's **Actions** tab. The workflow runs automatically on push
   to `main`, or tap **Run workflow** to trigger it manually.
3. Wait for the build to go green (a few minutes).
4. Open the finished run → scroll to **Artifacts** → download
   `robot-face-debug-apk`. It's a zip containing `app-debug.apk`.
5. On your phone, open that zip, tap the APK, allow "install from unknown
   sources" if asked, install it.
6. First launch: grant the microphone permission, then paste a free Gemini
   API key (get one at aistudio.google.com/apikey — no card needed) into the
   settings popup (gear icon, top right).

## Notes

- If Google renames the free-tier model later, open `index.html` and change
  the `MODEL` constant near the bottom of the `<script>` — check
  ai.google.dev for the current free Flash model name.
- The face is pure canvas — no 3D library, no external assets. If you later
  want to swap in a real 3D head, that's a bigger rewrite (WebGL/Three.js),
  not a drop-in file swap like the original A-Frame idea suggested.
- Everything runs from the WebView; `MainActivity.java` only exists to give
  the page real microphone/speech access, which plain WebView can't do
  reliably on its own.
