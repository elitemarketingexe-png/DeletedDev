import os
import subprocess
import html
import json
import time
import sys

# Telegram Bot API hard limit for file uploads
TELEGRAM_MAX_FILE_MB = 49.5


def send_message(base_url, text, chat_id, thread_id, retries=3):
    """Send a plain text/HTML message (used for oversized APK links)."""
    url = f"{base_url}/sendMessage"
    args = [
        "curl", "-s",
        "--form-string", f"chat_id={chat_id}",
        "--form-string", "parse_mode=HTML",
        "--form-string", f"text={text}",
        "--form-string", "disable_web_page_preview=true",
    ]
    if thread_id:
        args += ["--form-string", f"message_thread_id={thread_id}"]
    args.append(url)

    for attempt in range(1, retries + 1):
        result = subprocess.run(args, capture_output=True, text=True)
        try:
            response = json.loads(result.stdout)
        except json.JSONDecodeError:
            print(f"  Attempt {attempt}: invalid JSON: {result.stdout[:200]}", flush=True)
            if attempt < retries:
                time.sleep(5 * attempt)
                continue
            sys.exit(1)
        if response.get("ok"):
            return
        err = response.get("description", "unknown error")
        print(f"  Attempt {attempt} FAILED (sendMessage): {err}", flush=True)
        if attempt < retries:
            time.sleep(5 * attempt)
        else:
            print(f"ERROR: Could not send message after {retries} attempts.", flush=True)
            sys.exit(1)


def send_apk(base_url, apk_path, display_name, cap, chat_id, thread_id, release_url, retries=3):
    """Upload APK directly if under the size limit, otherwise send a download link."""
    if not os.path.exists(apk_path):
        print(f"ERROR: APK not found: {apk_path}", flush=True)
        sys.exit(1)

    size_mb = os.path.getsize(apk_path) / (1024 * 1024)

    # --- Over size limit: send download link instead ---
    if size_mb > TELEGRAM_MAX_FILE_MB:
        print(
            f"  {display_name} is {size_mb:.1f} MB — over {TELEGRAM_MAX_FILE_MB} MB limit. "
            f"Sending GitHub Release link instead.",
            flush=True,
        )
        link_text = cap if cap else ""
        # Append download link note to caption (or build standalone message)
        file_line = f'<b>{html.escape(display_name)}</b> ({size_mb:.0f} MB) — too large for direct upload'
        link_line = f'<a href="{html.escape(release_url)}">⬇ Download from GitHub Release</a>'
        message = f"{link_text}\n\n{file_line}\n{link_line}" if link_text else f"{file_line}\n{link_line}"
        send_message(base_url, message, chat_id, thread_id)
        return

    # --- Under size limit: upload directly ---
    print(f"Sending {apk_path} ({size_mb:.1f} MB) as {display_name}...", flush=True)
    url = f"{base_url}/sendDocument"
    args = [
        "curl", "-s",
        "-F", f"document=@{apk_path};filename={display_name}",
        "--form-string", f"chat_id={chat_id}",
        "--form-string", "parse_mode=HTML",
    ]
    if cap:
        args += ["--form-string", f"caption={cap}"]
    if thread_id:
        args += ["--form-string", f"message_thread_id={thread_id}"]
    args.append(url)

    for attempt in range(1, retries + 1):
        result = subprocess.run(args, capture_output=True, text=True)
        if result.stderr:
            print(f"  curl stderr: {result.stderr}", flush=True)

        try:
            response = json.loads(result.stdout)
        except json.JSONDecodeError:
            print(f"  Attempt {attempt}: invalid JSON: {result.stdout[:200]}", flush=True)
            if attempt < retries:
                time.sleep(5 * attempt)
                continue
            sys.exit(1)

        if response.get("ok"):
            print(f"  OK — sent {display_name}", flush=True)
            return
        else:
            err = response.get("description", "unknown error")
            print(f"  Attempt {attempt} FAILED: {err}", flush=True)
            if attempt < retries:
                time.sleep(5 * attempt)
            else:
                print(f"ERROR: Failed to send {display_name} after {retries} attempts.", flush=True)
                sys.exit(1)


def main():
    bot_token   = os.environ["TELEGRAM_BOT_TOKEN"]
    chat_id     = os.environ["TELEGRAM_CHAT_ID"]
    thread_id   = os.environ.get("TELEGRAM_THREAD_ID", "")
    version     = os.environ["VERSION_NAME"]
    commit_sha  = os.environ["COMMIT_SHA"]
    release_url = os.environ.get("RELEASE_URL", "")

    try:
        commit_author  = subprocess.check_output(["git", "log", "-1", "--pretty=format:%an"]).decode("utf-8").strip()
        commit_message = subprocess.check_output(["git", "log", "-1", "--pretty=format:%B"]).decode("utf-8").strip()
        commit_message = "\n".join([line for line in commit_message.split("\n") if line.strip()])
    except Exception:
        commit_author  = "Unknown"
        commit_message = "New release build"

    commit_author  = html.escape(commit_author)
    commit_message = html.escape(commit_message)
    release_url_escaped = html.escape(release_url)

    caption = (
        f"<b>PixelMusic v{html.escape(version)}</b>\n"
        f"Commit by: {commit_author}\n"
        f"Commit message:\n<blockquote>{commit_message}</blockquote>\n"
        f"Commit hash: #{commit_sha[:7]}\n"
        f"Device: mobile, wearos\n"
        f"ABI: arm64, armeabi, universal, x86_64\n"
        f"Version: Android >= 11\n"
        + (f'\n<a href="{release_url_escaped}">📦 Full GitHub Release</a>' if release_url else "")
    )

    base_url = f"https://api.telegram.org/bot{bot_token}"

    apks = [
        ("wear/build/outputs/apk/release/wear-release.apk",            "app-wearos-release.apk",           caption),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",    "app-mobile-arm64-release.apk",     ""),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk",  "app-mobile-armeabi-release.apk",   ""),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",       "app-mobile-x86_64-release.apk",    ""),
        ("app/build/outputs/apk/release/app-universal-release.apk",    "app-mobile-universal-release.apk", ""),
    ]

    for i, (apk_path, display_name, cap) in enumerate(apks):
        send_apk(base_url, apk_path, display_name, cap, chat_id, thread_id, release_url)
        if i < len(apks) - 1:
            time.sleep(2)

    print("All APKs published successfully.", flush=True)


if __name__ == "__main__":
    main()
