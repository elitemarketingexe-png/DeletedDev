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
  IS_RELEASE            - "true" if APP_VERSION_NAME tag is new (optional)
"""

import asyncio
import html
import os
import subprocess
import sys

from pyrogram import Client
from pyrogram.enums import ParseMode
from pyrogram.types import InlineKeyboardMarkup, InlineKeyboardButton


def format_changelog_line(line):
    line_clean = line.strip()
    if not line_clean:
        return ""
    
    # If the line already starts with a bullet point/emoji/dash, keep it
    if line_clean.startswith(('•', '-', '*')):
        return line_clean
        
    lower_line = line_clean.lower()
    
    # Choose emoji based on keywords
    if any(k in lower_line for k in ['perf', 'speed', 'fast', 'optimis', 'optimiz', 'instant', 'lag-free']):
        emoji = "⚡"
    elif any(k in lower_line for k in ['search', 'find', 'query']):
        emoji = "🔍"
    elif any(k in lower_line for k in ['shuffle', 'play', 'mix']):
        emoji = "🔀"
    elif any(k in lower_line for k in ['db', 'database', 'migration', 'schema', 'sqlite', 'room']):
        emoji = "💾"
    elif any(k in lower_line for k in ['backup', 'restore', 'zip', 'export', 'import']):
        emoji = "📦"
    elif any(k in lower_line for k in ['gradle', 'build', 'ci', 'workflow', 'jitpack', 'depend', 'version']):
        emoji = "🔧"
    elif any(k in lower_line for k in ['fix', 'bug', 'crash', 'error', 'resolve', 'issue']):
        emoji = "🐛"
    elif any(k in lower_line for k in ['feat', 'add', 'new', 'introduce', 'implement']):
        emoji = "✨"
    elif any(k in lower_line for k in ['ui', 'ux', 'layout', 'design', 'theme', 'color', 'screen', 'font', 'card']):
        emoji = "🎨"
    else:
        emoji = "✨" # Default emoji
        
    if ":" in line_clean:
        parts = line_clean.split(":", 1)
        prefix = parts[0].strip()
        suffix = parts[1].strip()
        if suffix:
            suffix = suffix[0].upper() + suffix[1:]
        return f"• {emoji} <b>{prefix}:</b> {suffix}"
    else:
        if line_clean:
            line_clean = line_clean[0].upper() + line_clean[1:]
        return f"• {emoji} {line_clean}"



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
    is_release = os.environ.get("IS_RELEASE", "false").strip().lower() == "true"

    commit_author, commit_message = get_commit_info()

    apks = [
        ("wear/build/outputs/apk/release/wear-release.apk",           "app-wearos-release.apk",          f"🤖 <b>Wear OS — v{version}</b>"),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",   "app-mobile-arm64-release.apk",    f"📱 <b>ARM64-v8a — v{version}</b>"),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "app-mobile-armeabi-release.apk",  f"📱 <b>ARMeabi-v7a — v{version}</b>"),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",      "app-mobile-x86_64-release.apk",   f"💻 <b>x86_64 — v{version}</b>"),
        ("app/build/outputs/apk/release/app-universal-release.apk",   "app-mobile-universal-release.apk",f"📱 <b>Universal — v{version}</b>"),
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
        workers=8,
        max_concurrent_transmissions=8,
    ) as app:
        # Get changelog from environment, fallback to commit message if empty
        changelog = os.environ.get("CHANGELOG", "").strip()
        if not changelog:
            changelog = f"<blockquote>{commit_message}</blockquote>"

        if is_release:
            # Clean up blockquote tags and format each line
            clean_changelog = changelog
            if clean_changelog.startswith("<blockquote>"):
                clean_changelog = clean_changelog[len("<blockquote>"):]
            if clean_changelog.endswith("</blockquote>"):
                clean_changelog = clean_changelog[:-len("</blockquote>")]
            
            clean_changelog = clean_changelog.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            changelog_lines = [line.strip() for line in clean_changelog.split("\n") if line.strip()]

            # Format the commits in changelog with emojis and bullets
            formatted_lines = []
            for line in changelog_lines:
                formatted = format_changelog_line(line)
                if formatted:
                    formatted_lines.append(formatted)
            
            changelog_block = "<blockquote>" + "\n\n".join(formatted_lines) + "</blockquote>"

            text = (
                f"🎵 <b>PixelMusic v{html.escape(version)} Release</b> 🎵\n\n"
                f"We are excited to release <b>PixelMusic v{html.escape(version)}</b>, "
                f"bringing massive performance optimizations, database migrations, and key feature fixes!\n\n"
                f"🚀 <b>What's New & Improved:</b>\n\n"
                f"{changelog_block}\n"
                f"------------------------------------\n"
                f"💡 <b>Which APK to install?</b>\n"
                f"<blockquote>• <b>arm64-v8a:</b> Modern phones (recommended)\n"
                f"• <b>universal:</b> Works on all phones (larger size)\n"
                f"• <b>armeabi-v7a:</b> Older / budget phones\n"
                f"• <b>x86_64:</b> Emulators & Chromebooks\n"
                f"• <b>wear:</b> Wear OS smartwatches only</blockquote>\n"
                f"------------------------------------\n"
                f"📦 <b>APKs Included:</b>\n"
                f"• <a href=\"https://github.com/ianshulyadav/PixelMusic/releases/download/v{html.escape(version)}/app-universal-release.apk\">app-universal-release.apk</a>\n"
                f"(Works on all mobile devices)\n"
                f"• <a href=\"https://github.com/ianshulyadav/PixelMusic/releases/download/v{html.escape(version)}/app-arm64-v8a-release.apk\">app-arm64-v8a-release.apk</a>\n"
                f"(Modern 64-bit phones)\n"
                f"• <a href=\"https://github.com/ianshulyadav/PixelMusic/releases/download/v{html.escape(version)}/app-armeabi-v7a-release.apk\">app-armeabi-v7a-release.apk</a>\n"
                f"(Older/budget phones)\n"
                f"• <a href=\"https://github.com/ianshulyadav/PixelMusic/releases/download/v{html.escape(version)}/app-x86_64-release.apk\">app-x86_64-release.apk</a>\n"
                f"(Emulators, Chromebooks)\n"
                f"• <a href=\"https://github.com/ianshulyadav/PixelMusic/releases/download/v{html.escape(version)}/wear-release.apk\">wear-release.apk</a>\n"
                f"(Wear OS smartwatch app)"
            )

            print("Sending changelog text message...", flush=True)
            changelog_msg = await app.send_message(
                chat_id=chat_id,
                text=text,
                parse_mode=ParseMode.HTML,
                reply_to_message_id=reply_to,
                disable_web_page_preview=True,
                reply_markup=InlineKeyboardMarkup(
                    [
                        [
                            InlineKeyboardButton(
                                "GitHub",
                                url=f"https://github.com/ianshulyadav/PixelMusic/releases/tag/v{version}"
                            )
                        ]
                    ]
                )
            )
            print(f"Changelog message sent. ID: {changelog_msg.id}", flush=True)

            for apk_path, display_name, cap in apks:
                size_mb = os.path.getsize(apk_path) / (1024 * 1024)
                print(f"Uploading {display_name} ({size_mb:.1f} MB)...", flush=True)

                await app.send_document(
                    chat_id=chat_id,
                    document=apk_path,
                    file_name=display_name,
                    caption=cap if cap else None,
                    parse_mode=ParseMode.HTML,
                    reply_to_message_id=changelog_msg.id,
                    force_document=True,
                )
                print(f"  OK — sent {display_name}", flush=True)

        else:
           
            caption = (
                f"Commit by: {commit_author}\n"
                f"Commit message.\n<blockquote>{commit_message}</blockquote>\n"
                f"Commit hash: #{commit_sha[:7]}\n"
                f"Device: mobile, wearos\n"
                f"ABI: arm64, armeabi, universal, x86_64\n"
                f"Files: 5\n"
                f"Version: Android >= 11\n\n"
                f"💡 <b>Which APK to install?</b>\n"
                f"<blockquote>• <b>arm64-v8a:</b> Modern phones (recommended)\n"
                f"• <b>universal:</b> Works on all phones (larger size)\n"
                f"• <b>armeabi-v7a:</b> Older / budget phones\n"
                f"• <b>x86_64:</b> Emulators & Chromebooks\n"
                f"• <b>wear:</b> Wear OS smartwatches only</blockquote>"
            )

            for index, (apk_path, display_name, _) in enumerate(apks):
                size_mb = os.path.getsize(apk_path) / (1024 * 1024)
                print(f"Uploading nightly build {display_name} ({size_mb:.1f} MB)...", flush=True)

                file_caption = caption if index == 0 else None

                await app.send_document(
                    chat_id=chat_id,
                    document=apk_path,
                    file_name=display_name,
                    caption=file_caption,
                    parse_mode=ParseMode.HTML,
                    reply_to_message_id=reply_to,
                    force_document=True,
                )
                print(f"  OK — sent {display_name}", flush=True)

    print("All APKs published successfully.", flush=True)


if __name__ == "__main__":
    asyncio.run(publish())
