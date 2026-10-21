SUMOVAN MM DOCs - ANDROID APP  (ONE-TIME SETUP, about 10 minutes)

1. Sign in at github.com. Click + > New repository. Name: sumovan-apk. Public. Create.
2. On the empty repo page click "uploading an existing file".
   Unzip sumovan-apk.zip on your computer and drag EVERYTHING inside the folder
   (app, settings.gradle, build.gradle, gradle.properties, .gitignore, .github) into the page.
   If the hidden ".github" folder does not upload: click Add file > Create new file, type
   .github/workflows/build-apk.yml  as the name, paste the contents of that file, Commit.
3. Click Commit changes. Open the "Actions" tab. "Build APK" runs by itself (about 4-6 minutes).
   If you see a green check, it worked. (First time: click "I understand my workflows, enable them" if asked.)
4. Download link for drivers (never changes):
   https://github.com/<YOUR-GITHUB-USERNAME>/sumovan-apk/releases/download/apk/SUMOVAN-MM-DOCs.apk
5. Send that link to your drivers (Messenger / Viber / SPX group).

UPDATING THE APP: nothing to do. The app opens your live Apps Script link, so every
"New version" you deploy there reaches all drivers automatically.
Only if the /exec link changes: edit the APP_URL line in app/build.gradle on GitHub, commit,
wait for the green check, and drivers install the new APK over the old one.

DRIVER INSTALL (Android):
1. Tap the download link. If Chrome warns "this type of file can harm your device", tap Download anyway.
2. Open the downloaded file. If asked, tap Settings > allow "Install unknown apps" for Chrome (or your browser), go Back.
3. Tap Install. If "Play Protect" warns, tap More details > Install anyway.
4. Open MM DOCs from the home screen / app drawer. Log in with your SPX ID and password.
Saved PDFs go to Downloads > SUMOVAN and open automatically.
