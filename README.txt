Zero Floating

Setup pertama kali di Termux

Perintah 1, cukup sekali, lalu tekan Izinkan:
termux-setup-storage

Perintah 2:
pkg install -y unzip && unzip -oq "$(ls -t ~/storage/downloads/ZeroFloating*.zip | head -1)" -d ~ && bash ~/ZeroFloating/setup-termux.sh

Update ke versi zip yang baru (repo sudah ada di GitHub)
unzip -oq "$(ls -t ~/storage/downloads/ZeroFloating*.zip | head -1)" -d ~ && bash ~/ZeroFloating/update-termux.sh

Project ada di folder home Termux: ~/ZeroFloating
Tiap push ke branch main, GitHub Actions membangun APK dan membuat rilis baru dengan nomor naik sendiri.

APK universal, jalan di armeabi-v7a, arm64-v8a, x86 dan x86_64.
Model default gemini-3.8-flash. Daftar model lengkap ada di langkah pertama aplikasi.
Keystore penanda tangan ada di folder signing agar update APK tidak minta uninstall.
