"""
Publish PixelMusic APKs to a Telegram channel using Telethon (MTProto).

Why Telethon instead of Pyrogram?
  - Telethon has a more stable asyncio implementation on headless CI runners.
  - Telethon's upload engine doesn't block the event loop the way Pyrogram does.
  - Supports files up to 2 GB via MTProto (bypasses the 50 MB Bot API limit).

Required env vars:
  TELEGRAM_API_ID       - from my.telegram.org (integer)
  TELEGRAM_API_HASH     - from my.telegram.org (string)
  TELEGRAM_BOT_TOKEN    - BotFather token (used to authenticate the bot)
  TELEGRAM_CHAT_ID      - e.g. "@PixelMusicApp"
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


def format_changelog_line(line):
    line_clean = line.strip()
    if not line_clean:
        return ""
    if line_clean.startswith(('•', '-', '*')):
        return line_clean
    lower_line = line_clean.lower()
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
        emoji = "✨"

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


def make_progress_callback(display_name, file_size):
    last_pct = [-1]
    def callback(current, total):
        pct = int(current / total * 100) if total else 0
        if pct >= last_pct[0] + 10:
            last_pct[0] = pct
            mb_done = current / (1024 * 1024)
            mb_total = total / (1024 * 1024)
            print(f"  [{display_name}] {pct}% — {mb_done:.1f}/{mb_total:.1f} MB", flush=True)
    return callback


async def send_file_with_retry(client, chat_entity, apk_path, display_name, caption, reply_to, thread_id, max_retries=3):
    """Send a file, retrying up to max_retries times on failure."""
    for attempt in range(1, max_retries + 1):
        try:
            print(f"  Attempt {attempt}/{max_retries} — uploading {display_name}...", flush=True)
            kwargs = dict(
                entity=chat_entity,
                file=apk_path,
                caption=caption,
                parse_mode="html",
                force_document=True,
                attributes=[],
                progress_callback=make_progress_callback(display_name, os.path.getsize(apk_path)),
            )
            if reply_to:
                kwargs["reply_to"] = reply_to
            # Telethon: reply_to inside a topic requires passing the topic message ID
            result = await asyncio.wait_for(
                client.send_file(**kwargs),
                timeout=600,  # 10-minute hard timeout per file
            )
            print(f"  OK — sent {display_name} (msg id: {result.id})", flush=True)
            return result
        except asyncio.TimeoutError:
            print(f"  TIMEOUT on attempt {attempt} for {display_name}.", flush=True)
            if attempt == max_retries:
                raise
            await asyncio.sleep(5)
        except Exception as exc:
            print(f"  ERROR on attempt {attempt}: {exc}", flush=True)
            if attempt == max_retries:
                raise
            await asyncio.sleep(5)


async def main():
    from telethon import TelegramClient
    from telethon.sessions import StringSession

    api_id    = int(os.environ["TELEGRAM_API_ID"])
    api_hash  = os.environ["TELEGRAM_API_HASH"]
    bot_token = os.environ["TELEGRAM_BOT_TOKEN"]
    chat_id   = os.environ["TELEGRAM_CHAT_ID"]
    thread_id_str = os.environ.get("TELEGRAM_THREAD_ID", "")
    version   = os.environ["VERSION_NAME"]
    commit_sha = os.environ["COMMIT_SHA"]
    is_release = os.environ.get("IS_RELEASE", "false").strip().lower() == "true"

    thread_id = int(thread_id_str) if thread_id_str else None
    commit_author, commit_message = get_commit_info()

    apks = [
        ("wear/build/outputs/apk/release/wear-release.apk",           "app-wearos-release.apk",           f"🤖 <b>Wear OS — v{version}</b>"),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk",   "app-mobile-arm64-release.apk",     f"📱 <b>ARM64-v8a — v{version}</b>"),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "app-mobile-armeabi-release.apk",   f"📱 <b>ARMeabi-v7a — v{version}</b>"),
        ("app/build/outputs/apk/release/app-x86_64-release.apk",      "app-mobile-x86_64-release.apk",    f"💻 <b>x86_64 — v{version}</b>"),
        ("app/build/outputs/apk/release/app-universal-release.apk",   "app-mobile-universal-release.apk", f"📱 <b>Universal — v{version}</b>"),
    ]

    # Verify all files exist before starting
    for apk_path, _, _ in apks:
        if not os.path.exists(apk_path):
            print(f"ERROR: APK not found: {apk_path}", flush=True)
            sys.exit(1)
        size_mb = os.path.getsize(apk_path) / (1024 * 1024)
        print(f"  Found: {apk_path} ({size_mb:.1f} MB)", flush=True)

    changelog = os.environ.get("CHANGELOG", "").strip()
    if not changelog:
        changelog = f"<blockquote>{commit_message}</blockquote>"

    # Use StringSession (in-memory) — no session file I/O on CI
    async with TelegramClient(
        StringSession(),
        api_id,
        api_hash,
        # Force IPv4 — GHA runners have unreliable IPv6 support
        use_ipv6=False,
        # Generous timeouts to handle large files
        connection_retries=5,
        retry_delay=3,
        request_retries=5,
        flood_sleep_threshold=60,
    ) as client:
        print("Signing in as bot...", flush=True)
        await client.sign_in(bot_token=bot_token)
        print("Signed in.", flush=True)

        # Resolve chat entity once
        chat_entity = await client.get_entity(chat_id)
        print(f"Resolved chat: {chat_entity}", flush=True)

        if is_release:
            # ─── RELEASE POST ───────────────────────────────────────────────────
            clean_changelog = changelog
            if clean_changelog.startswith("<blockquote>"):
                clean_changelog = clean_changelog[len("<blockquote>"):]
            if clean_changelog.endswith("</blockquote>"):
                clean_changelog = clean_changelog[:-len("</blockquote>")]
            clean_changelog = clean_changelog.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            changelog_lines = [line.strip() for line in clean_changelog.split("\n") if line.strip()]
            formatted_lines = [fmt for line in changelog_lines if (fmt := format_changelog_line(line))]
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
            )

            print("Sending changelog text message...", flush=True)
            send_kwargs = dict(entity=chat_entity, message=text, parse_mode="html")
            if thread_id:
                send_kwargs["reply_to"] = thread_id
            changelog_msg = await client.send_message(**send_kwargs)
            changelog_msg_id = changelog_msg.id
            print(f"Changelog message sent. ID: {changelog_msg_id}", flush=True)

            for apk_path, display_name, cap in apks:
                size_mb = os.path.getsize(apk_path) / (1024 * 1024)
                print(f"Uploading {display_name} ({size_mb:.1f} MB)...", flush=True)
                await send_file_with_retry(
                    client=client,
                    chat_entity=chat_entity,
                    apk_path=apk_path,
                    display_name=display_name,
                    caption=cap,
                    reply_to=changelog_msg_id,
                    thread_id=thread_id,
                )

        else:
            # ─── NIGHTLY POST ────────────────────────────────────────────────────
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

            first_msg = None

            for index, (apk_path, display_name, _) in enumerate(apks):
                size_mb = os.path.getsize(apk_path) / (1024 * 1024)
                print(f"Uploading nightly build {display_name} ({size_mb:.1f} MB)...", flush=True)
                msg = await send_file_with_retry(
                    client=client,
                    chat_entity=chat_entity,
                    apk_path=apk_path,
                    display_name=display_name,
                    caption=caption if index == 0 else None,
                    reply_to=first_msg.id if (index > 0 and first_msg) else thread_id,
                    thread_id=thread_id,
                )
                if index == 0:
                    first_msg = msg

    print("All APKs published successfully.", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
