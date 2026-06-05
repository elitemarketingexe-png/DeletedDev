"""
Publish PixelMusic APKs to a Telegram channel using Pyrogram (MTProto).

Why Pyrogram instead of the Bot HTTP API?
  - Bot HTTP API (sendDocument via curl): hard 50 MB limit per file.
  - Pyrogram via MTProto + api_id/api_hash: up to 2 GB per file.

Required env vars:
  TELEGRAM_API_ID       - from my.telegram.org (integer)
  TELEGRAM_API_HASH     - from my.telegram.org (string)
  TELEGRAM_BOT_TOKEN    - BotFather token
  TELEGRAM_CHAT_ID      - e.g. "@PixelMusicApp"
  TELEGRAM_THREAD_ID    - (optional) message thread id for topics
  VERSION_NAME          - app version string
  COMMIT_SHA            - full commit SHA
"""

import asyncio
import html
import os
import subprocess
import sys

from pyrogram import Client
from pyrogram.enums import ParseMode


def get_commit_info():
    try:
        author = subprocess.check_output(
            ["git", "log", "-1", "--pretty=format:%an"]
        ).decode("utf-8").strip()
        message = subprocess.check_output(
            ["git", "log", "-1", "--pretty=format:%B"]
        ).decode("utf-8").strip()
        message = "\n".join(line for line in message.split("\n") if line.strip())
    except Exception:
        author = "Unknown"
        message = "New release build"
    return html.escape(author), html.escape(message)


async def publish():
    api_id     = int(os.environ["TELEGRAM_API_ID"])
    api_hash   = os.environ["TELEGRAM_API_HASH"]
    bot_token  = os.environ["TELEGRAM_BOT_TOKEN"]
    chat_id    = os.environ["TELEGRAM_CHAT_ID"]
    thread_id  = os.environ.get("TELEGRAM_THREAD_ID", "")
    version    = os.environ["VERSION_NAME"]
    commit_sha = os.environ["COMMIT_SHA"]

    commit_author, commit_message = get_commit_info()

    caption = (
        f"<b>PixelMusic v{html.escape(version)}</b>\n"
        f"Commit by: {commit_author}\n"
        f"Commit message:\n<blockquote>{commit_message}</blockquote>\n"
        f"Commit hash: #{commit_sha[:7]}\n"
        f"Device: mobile, wearos\n"
        f"ABI: arm64, armeabi, universal, x86_64\n"
        f"Files: 5\n"
        f"Version: Android >= 11"
    )

    apks = [
        ("wear/build/outputs/apk/release/wear-release.apk",           "app-wearos-release.apk",          caption),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",   "app-mobile-arm64-release.apk",    ""),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "app-mobile-armeabi-release.apk",  ""),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",      "app-mobile-x86_64-release.apk",   ""),
        ("app/build/outputs/apk/release/app-universal-release.apk",   "app-mobile-universal-release.apk",""),
    ]

    # Verify all files exist before starting
    for apk_path, _, _ in apks:
        if not os.path.exists(apk_path):
            print(f"ERROR: APK not found: {apk_path}", flush=True)
            sys.exit(1)
        size_mb = os.path.getsize(apk_path) / (1024 * 1024)
        print(f"  Found: {apk_path} ({size_mb:.1f} MB)", flush=True)

    reply_to = int(thread_id) if thread_id else None

    async with Client(
        name="pixelmusic_publisher",
        api_id=api_id,
        api_hash=api_hash,
        bot_token=bot_token,
        in_memory=True,
    ) as app:
        for apk_path, display_name, cap in apks:
            size_mb = os.path.getsize(apk_path) / (1024 * 1024)
            print(f"Uploading {display_name} ({size_mb:.1f} MB)...", flush=True)

            await app.send_document(
                chat_id=chat_id,
                document=apk_path,
                file_name=display_name,
                caption=cap if cap else None,
                parse_mode=ParseMode.HTML,
                reply_to_message_id=reply_to,
                force_document=True,
            )
            print(f"  OK — sent {display_name}", flush=True)

    print("All APKs published successfully.", flush=True)


if __name__ == "__main__":
    asyncio.run(publish())
