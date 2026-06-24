AdGuard/TV DNS - Play Store AAB build
Version: 3.5.6-play-aab / versionCode 41

What changed:
- Added product flavors: github and play.
- githubRelease keeps the GitHub APK updater.
- playRelease disables APK updater behavior and opens Google Play instead.
- playRelease targets SDK 35 for Google Play phone/tablet requirements.
- play manifest removes REQUEST_INSTALL_PACKAGES permission.
- Added GitHub Actions workflow: .github/workflows/build-play-aab.yml
- Added helper v7 options to trigger and download the Play Store AAB.

How to build AAB without Android Studio:
1. Copy these files into your project.
2. Commit and push to main.
3. Open helper v7 and choose option 28.
4. Choose option 29 to wait and download the .aab.

Output folder on your PC:
%USERPROFILE%\Downloads\AdGuard-TV-DNS-AAB

File to upload in Google Play Console:
The .aab downloaded from the workflow artifact.

Important:
- For Google Play, use the Play Store AAB, not APK.
- For GitHub/manual TV installs, keep using the normal GitHub APK release.
- Google Play may still ask about VpnService and QUERY_ALL_PACKAGES declarations.
