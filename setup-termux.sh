#!/data/data/com.termux/files/usr/bin/bash
set -e

USERNAME="NightXD721"
EMAIL="nightxd721@gmail.com"
REPO="zero-floating"

cd "$(dirname "$0")"

pkg update -y
pkg install -y git gh

git config --global user.name "$USERNAME"
git config --global user.email "$EMAIL"

if ! gh auth status >/dev/null 2>&1; then
  gh auth login -h github.com -p https -w -s workflow
fi
gh auth setup-git

if [ ! -d .git ]; then
  git init -b main
fi
git add -A
git commit -m "Zero Floating" || true
git branch -M main

git remote remove origin 2>/dev/null || true
if gh repo view "$USERNAME/$REPO" >/dev/null 2>&1; then
  git remote add origin "https://github.com/$USERNAME/$REPO.git"
  git push -u origin main
else
  gh repo create "$USERNAME/$REPO" --public --source=. --remote=origin --push
fi

echo
echo "Push selesai. Build jalan di:"
echo "https://github.com/$USERNAME/$REPO/actions"
echo "APK muncul di:"
echo "https://github.com/$USERNAME/$REPO/releases"
