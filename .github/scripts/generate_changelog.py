import subprocess
import html
import os
import re

def main():
    prev_sha = os.environ.get('PREV_SHA', '')
    curr_sha = os.environ.get('CURR_SHA', '')

    if prev_sha and prev_sha != '0000000000000000000000000000000000000000':
        cmd = ['git', 'log', '--pretty=format:%s', f'{prev_sha}..{curr_sha}']
    else:
        cmd = ['git', 'log', '-n', '20', '--pretty=format:%s']

    try:
        output = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
        raw_commits = [line.strip() for line in output.split('\n') if line.strip()]
    except Exception:
        raw_commits = ['New release build']

    ignored_patterns = [
        r"^ci:", r"^workflow", r"telegram", r"github", r"gitHub", r"Telegram", r"GitHub",
        r"dependabot", r"bump the github-actions", r"bump the gradle-dependencies", r"\[skip ci\]"
    ]

    commits = []
    for c in raw_commits:
        if any(re.search(pat, c, re.IGNORECASE) for pat in ignored_patterns):
            continue
        commits.append(c)

    if not commits:
        commits = ['Performance enhancements and bug fixes']

    changelog_html = '<blockquote>' + '\n'.join(html.escape(c) for c in commits) + '</blockquote>'
    changelog_md   = '\n'.join('- ' + c for c in commits)

    github_output = os.environ.get('GITHUB_OUTPUT')
    if github_output:
        with open(github_output, 'a', encoding='utf-8') as f:
            f.write('changelog_html<<EOF\n' + changelog_html + '\nEOF\n')
            f.write('changelog_md<<EOF\n' + changelog_md + '\nEOF\n')
    else:
        print("GITHUB_OUTPUT not set. Changelog HTML:\n", changelog_html)
        print("Changelog MD:\n", changelog_md)

if __name__ == '__main__':
    main()
