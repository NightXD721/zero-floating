#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$(dirname "$0")"

if [ ! -d .git ]; then
  exec bash ./setup-termux.sh
fi

git add -A
git commit -m "update $(date +%d%m%y-%H%M)" || echo "Tidak ada perubahan baru"
git push -u origin main || git push -u origin main --force

echo
echo "Update terkirim. Pantau build di:"
echo "https://github.com/NightXD721/zero-floating/actions"
echo "APK baru muncul di:"
echo "https://github.com/NightXD721/zero-floating/releases"
