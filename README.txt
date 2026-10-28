Zero Floating

Setup di Termux (cuma 2 perintah)

Perintah 1, cukup sekali, lalu tekan Izinkan:
termux-setup-storage

Perintah 2, salin semuanya:
pkg install -y unzip && unzip -oq "$(ls -t ~/storage/downloads/ZeroFloating*.zip | head -1)" -d ~ && bash ~/ZeroFloating/setup-termux.sh

Project otomatis ada di folder home Termux: ~/ZeroFloating
Saat login GitHub, buka link yang muncul di browser lalu masukkan kodenya.

Setelah push, GitHub Actions membangun APK dan mengunggahnya ke halaman rilis.
Tiap push ke branch main membuat rilis baru dengan nomor naik sendiri.

Update aplikasi
cd ~/ZeroFloating
git add -A
git commit -m "update"
git push

APK universal, jalan di armeabi-v7a, arm64-v8a, x86 dan x86_64.
Model default gemini-3.8-flash. Daftar model lengkap ada di langkah pertama aplikasi.
Keystore penanda tangan ada di folder signing agar update APK tidak minta uninstall.
