import os
import subprocess
import json

def main():
    bot_token    = os.environ['TELEGRAM_BOT_TOKEN']
    chat_id      = os.environ['TELEGRAM_CHAT_ID']
    thread_id    = os.environ.get('TELEGRAM_THREAD_ID', '')
    version      = os.environ['VERSION_NAME']
    changelog    = os.environ['CHANGELOG']
    commit_sha   = os.environ['COMMIT_SHA']
    release_url  = os.environ['RELEASE_URL']
    commit_url   = os.environ['COMMIT_URL']

    apks = [
        ("app/build/outputs/apk/release/app-universal-release.apk",   "universal", "universal — Works on all devices"),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",   "arm64-v8a", "arm64-v8a — Modern phones (Pixel, Samsung, OnePlus…)"),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "armeabi-v7a", "armeabi-v7a — Older / budget ARM phones"),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",      "x86_64", "x86_64 — Emulators &amp; Chromebooks"),
    ]

    valid_apks = []
    oversized_apks = []

    for path, name, desc in apks:
        if not os.path.exists(path):
            continue
        size = os.path.getsize(path)
        if size > 50 * 1024 * 1024:
            oversized_apks.append((path, name, desc))
        else:
            valid_apks.append((path, name, desc))

    if not valid_apks:
        print("No valid APKs to send.")
        return

    # Build primary caption
    caption = (
        f"<b>📱 PixelMusic v{version} — Release</b>\n\n"
        f"<b>Changes:</b>\n{changelog}\n\n"
        f"<b>APKs included:</b>\n"
    )
    for path, name, desc in valid_apks:
        caption += f"• <code>{desc}</code>\n"

    caption += f"\n<b>Commit:</b> <a href='{commit_url}'>{commit_sha[:7]}</a>\n"
    caption += f"<b>Release:</b> <a href='{release_url}'>GitHub Release ↗</a>"

    if oversized_apks:
        caption += "\n\n<b>⚠️ Note: The following APK(s) exceeded Telegram's 50MB limit and can be downloaded from the GitHub Release page:</b>"
        for path, name, desc in oversized_apks:
            caption += f"\n• <code>{name}</code>"

    media_group = []
    curl_files_args = []

    for i, (path, name, desc) in enumerate(valid_apks):
        attachment_name = f"apk{i}"
        cap = caption if i == 0 else f"<code>{name}</code>"
        media_group.append({
            "type": "document",
            "media": f"attach://{attachment_name}",
            "caption": cap,
            "parse_mode": "HTML"
        })
        curl_files_args += ["-F", f"{attachment_name}=@{path}"]

    url = f"https://api.telegram.org/bot{bot_token}/sendMediaGroup"

    args = [
        "curl", "-s",
        "-F", f"chat_id={chat_id}",
        "--form-string", f"media={json.dumps(media_group)}",
    ]
    if thread_id:
        args += ["--form-string", f"message_thread_id={thread_id}"]

    args += curl_files_args
    args.append(url)

    print("Sending APKs as a media group...")
    result = subprocess.run(args, capture_output=True, text=True)
    print(f"Response: {result.stdout}")
    if result.stderr:
        print(f"Error output: {result.stderr}")

if __name__ == '__main__':
    main()
