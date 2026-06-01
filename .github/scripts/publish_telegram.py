import os
import subprocess
import html

def main():
    bot_token    = os.environ['TELEGRAM_BOT_TOKEN']
    chat_id      = os.environ['TELEGRAM_CHAT_ID']
    thread_id    = os.environ.get('TELEGRAM_THREAD_ID', '')
    version      = os.environ['VERSION_NAME']
    commit_sha   = os.environ['COMMIT_SHA']

    try:
        commit_author = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%an']).decode('utf-8').strip()
        commit_message = subprocess.check_output(['git', 'log', '-1', '--pretty=format:%B']).decode('utf-8').strip()
        commit_message = "\n".join([line for line in commit_message.split("\n") if line.strip()])
    except Exception:
        commit_author = "Unknown"
        commit_message = "New release build"

    # HTML escape variables to avoid Telegram parsing errors
    commit_author = html.escape(commit_author)
    commit_message = html.escape(commit_message)

    caption = (
        f"Commit by: {commit_author}\n"
        f"Commit message:\n<blockquote>{commit_message}</blockquote>\n"
        f"Commit hash: #{commit_sha[:7]}\n"
        f"Device: mobile, wearos\n"
        f"ABI: arm64, armeabi, universal, x86_64\n"
        f"Files: 5\n"
        f"Version: Android >= 11"
    )

    apks = [
        ("wear/build/outputs/apk/release/wear-release.apk", "app-wearos-release.apk", caption),
        ("app/build/outputs/apk/release/app-armeabi-v7a-release.apk", "app-mobile-armeabi-release.apk", ""),
        ("app/build/outputs/apk/release/app-x86_64-release.apk", "app-mobile-x86_64-release.apk", ""),
        ("app/build/outputs/apk/release/app-arm64-v8a-release.apk", "app-mobile-arm64-release.apk", ""),
        ("app/build/outputs/apk/release/app-universal-release.apk", "app-mobile-universal-release.apk", ""),
    ]

    url = f"https://api.telegram.org/bot{bot_token}/sendDocument"

    for apk_path, display_name, cap in apks:
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
        
        print(f"Sending {apk_path} as {display_name}...")
        result = subprocess.run(args, capture_output=True, text=True)
        print(f"Sent {apk_path}. Output: {result.stdout}")
        if result.stderr:
            print(f"Error output: {result.stderr}")

if __name__ == '__main__':
    main()
