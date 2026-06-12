"""
Publish PixelMusic APKs to a Telegram channel using Telethon (MTProto).

Follows the ArchiveTune pattern:
  - TelegramClient(...).start(bot_token=...) — synchronous start
  - bot_session.session file cached across GHA runs via actions/cache
  - Sequential file uploads with progress callback

Required env vars:
  TELEGRAM_API_ID       - from my.telegram.org (integer)
  TELEGRAM_API_HASH     - from my.telegram.org (string)
  TELEGRAM_BOT_TOKEN    - BotFather token
  TELEGRAM_CHAT_ID      - numeric group/channel ID (e.g. -1001234567890)
  TELEGRAM_THREAD_ID    - (optional) message thread id for topics
  VERSION_NAME          - app version string
  COMMIT_SHA            - full commit SHA
  IS_RELEASE            - "true" if this is an official release
  CHANGELOG             - HTML changelog string (optional)
"""

import asyncio
import html
import os
import subprocess
import sys

from telethon import TelegramClient
from telethon.tl.types import DocumentAttributeFilename


# ── Credentials ────────────────────────────────────────────────────────────────
api_id     = int(os.environ["TELEGRAM_API_ID"])
api_hash   = os.environ["TELEGRAM_API_HASH"]
bot_token  = os.environ["TELEGRAM_BOT_TOKEN"]
_chat_id_raw = os.environ["TELEGRAM_CHAT_ID"]
try:
    chat_id = int(_chat_id_raw)
except ValueError:
    chat_id = _chat_id_raw  # e.g. "@PixelMusicApp"
thread_id  = int(os.environ["TELEGRAM_THREAD_ID"]) if os.environ.get("TELEGRAM_THREAD_ID") else None
version    = os.environ["VERSION_NAME"]
commit_sha = os.environ["COMMIT_SHA"]
is_release = os.environ.get("IS_RELEASE", "false").strip().lower() == "true"
changelog  = os.environ.get("CHANGELOG", "").strip()

# ── Session — reuse cached file if present (ArchiveTune pattern) ───────────────
SESSION_FILE = "bot_session"
if os.path.exists(f"{SESSION_FILE}.session"):
    print("Reusing cached Telegram session.", flush=True)
else:
    print("No cached session found, will create a new one.", flush=True)

# ── Create client (synchronous .start like ArchiveTune) ────────────────────────
client = TelegramClient(SESSION_FILE, api_id, api_hash).start(bot_token=bot_token)
client.parse_mode = "html"


# ── Helpers ────────────────────────────────────────────────────────────────────
def get_commit_info():
    try:
        author  = subprocess.check_output(["git", "log", "-1", "--pretty=format:%an"]).decode("utf-8").strip()
        message = subprocess.check_output(["git", "log", "-1", "--pretty=format:%B"]).decode("utf-8").strip()
        message = "\n".join(line for line in message.split("\n") if line.strip())
    except Exception:
        author, message = "Unknown", "New release build"
    return html.escape(author), html.escape(message)


def human_readable_size(size, decimal_places=2):
    for unit in ["B", "KB", "MB", "GB"]:
        if size < 1024.0:
            break
        size /= 1024.0
    return f"{size:.{decimal_places}f} {unit}"


def make_progress(filename):
    last_reported = -10
    async def callback(current, total):
        nonlocal last_reported
        pct = int((current / total) * 100)
        if pct >= last_reported + 10 or pct == 100:
            last_reported = pct
            print(f"Uploading {filename}: {pct}% ({human_readable_size(current)}/{human_readable_size(total)})", flush=True)
    return callback


async def upload_apk(apk_path, name):
    print(f"Starting parallel upload of {name} ({human_readable_size(os.path.getsize(apk_path))})...", flush=True)
    uploaded = await client.upload_file(
        apk_path,
        file_name=name,
        progress_callback=make_progress(name)
    )
    print(f"Finished uploading {name}", flush=True)
    return uploaded


def format_changelog_line(line):
    line_clean = line.strip()
    if not line_clean:
        return ""
    lower = line_clean.lower()
    if any(k in lower for k in ["perf", "speed", "fast", "optimis", "optimiz", "instant"]):
        emoji = "⚡"
    elif any(k in lower for k in ["fix", "bug", "crash", "error", "resolve"]):
        emoji = "🐛"
    elif any(k in lower for k in ["feat", "add", "new", "introduce", "implement"]):
        emoji = "✨"
    elif any(k in lower for k in ["ui", "ux", "layout", "design", "theme", "color", "screen"]):
        emoji = "🎨"
    elif any(k in lower for k in ["db", "database", "migration", "schema", "sqlite"]):
        emoji = "💾"
    elif any(k in lower for k in ["gradle", "build", "ci", "workflow", "depend"]):
        emoji = "🔧"
    else:
        emoji = "✨"

    if ":" in line_clean:
        prefix, _, suffix = line_clean.partition(":")
        suffix = suffix.strip()
        if suffix:
            suffix = suffix[0].upper() + suffix[1:]
        return f"• {emoji} <b>{prefix.strip()}:</b> {suffix}"
    else:
        line_clean = line_clean[0].upper() + line_clean[1:]
        return f"• {emoji} {line_clean}"


# ── APK list ───────────────────────────────────────────────────────────────────
APKS = [
    ("wear/build/outputs/apk/release/wear-release.apk",            "app-wearos-release.apk",           f"🤖 <b>Wear OS — v{version}</b>"),
    ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",    "app-mobile-arm64-release.apk",     f"📱 <b>ARM64-v8a — v{version}</b>"),
    ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk",  "app-mobile-armeabi-release.apk",   f"📱 <b>ARMeabi-v7a — v{version}</b>"),
    ("app/build/outputs/apk/release/app-x86_64-release.apk",       "app-mobile-x86_64-release.apk",    f"💻 <b>x86_64 — v{version}</b>"),
    ("app/build/outputs/apk/release/app-universal-release.apk",    "app-mobile-universal-release.apk", f"📱 <b>Universal — v{version}</b>"),
]

# Verify all files exist
for apk_path, _, _ in APKS:
    if not os.path.exists(apk_path):
        print(f"ERROR: APK not found: {apk_path}", flush=True)
        sys.exit(1)
    size_mb = os.path.getsize(apk_path) / (1024 * 1024)
    print(f"  Found: {apk_path} ({size_mb:.1f} MB)", flush=True)

commit_author, commit_message = get_commit_info()
if not changelog:
    changelog = f"<blockquote>{commit_message}</blockquote>"


# ── Upload logic ───────────────────────────────────────────────────────────────
async def send_message(text, reply_to=None):
    send_kwargs = dict(entity=chat_id, message=text, parse_mode="html", link_preview=False)
    if reply_to:
        send_kwargs["reply_to"] = reply_to
    elif thread_id:
        send_kwargs["reply_to"] = thread_id
    return await client.send_message(**send_kwargs)


async def run():
    if is_release:
        # ── RELEASE POST ────────────────────────────────────────────────────────
        clean = changelog
        for tag in ("<blockquote>", "</blockquote>"):
            clean = clean.replace(tag, "")
        clean = clean.replace("<br>", "\n").replace("<br/>", "\n")
        lines = [format_changelog_line(l) for l in clean.split("\n") if l.strip()]
        changelog_block = "<blockquote>" + "\n\n".join(l for l in lines if l) + "</blockquote>"

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
            f"• <b>wear:</b> Wear OS smartwatches only</blockquote>"
        )
        print("Sending changelog message...", flush=True)
        header_msg = await send_message(text)
        print(f"Changelog sent. ID: {header_msg.id}", flush=True)

        # Upload all APKs in parallel
        apk_paths = [apk_path for apk_path, _, _ in APKS]
        apk_names = [name for _, name, _ in APKS]
        total_mb = sum(os.path.getsize(p) / (1024 * 1024) for p in apk_paths)
        print(f"Uploading all {len(apk_paths)} APKs ({total_mb:.1f} MB total) in parallel...", flush=True)

        upload_tasks = [upload_apk(apk_path, name) for apk_path, name, _ in APKS]
        uploaded_files = await asyncio.gather(*upload_tasks)
        print("All uploads complete. Preparing to send...", flush=True)

        await client.send_file(
            entity=chat_id,
            file=uploaded_files,
            captions=[cap for _, _, cap in APKS],
            parse_mode="html",
            force_document=True,
            reply_to=thread_id,
            attributes=[[DocumentAttributeFilename(file_name=name)] for name in apk_names],
        )
        print("All APKs sent.", flush=True)

    else:
        # ── NIGHTLY POST ────────────────────────────────────────────────────────
        text = (
            f"🎵 <b>PixelMusic Nightly Build</b> 🎵\n\n"
            f"Commit by: {commit_author}\n"
            f"Commit message:\n<blockquote>{commit_message}</blockquote>\n"
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
        print("Sending nightly info message...", flush=True)
        header_msg = await send_message(text)
        print(f"Nightly info sent. ID: {header_msg.id}", flush=True)

        # Upload all APKs in parallel
        apk_paths = [apk_path for apk_path, _, _ in APKS]
        apk_names = [name for _, name, _ in APKS]
        total_mb = sum(os.path.getsize(p) / (1024 * 1024) for p in apk_paths)
        print(f"Uploading all {len(apk_paths)} APKs ({total_mb:.1f} MB total) in parallel...", flush=True)

        upload_tasks = [upload_apk(apk_path, name) for apk_path, name, _ in APKS]
        uploaded_files = await asyncio.gather(*upload_tasks)
        print("All uploads complete. Preparing to send...", flush=True)

        await client.send_file(
            entity=chat_id,
            file=uploaded_files,
            captions=[cap for _, _, cap in APKS],
            parse_mode="html",
            force_document=True,
            reply_to=thread_id,
            attributes=[[DocumentAttributeFilename(file_name=name)] for name in apk_names],
        )
        print("All APKs sent.", flush=True)

    print("All APKs published successfully.", flush=True)


try:
    with client:
        client.loop.run_until_complete(run())
finally:
    pass
