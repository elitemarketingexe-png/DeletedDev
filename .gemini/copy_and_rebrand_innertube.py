import os
import shutil
import re

src_dir = r"D:\Downloads\PixelMusic\ArchiveTune\innertube"
dest_dir = r"d:\Downloads\PixelMusic\PixelPlayer\innertube"

# Step 1: Copy innertube module directory
if os.path.exists(dest_dir):
    print(f"Destination {dest_dir} already exists, deleting first...")
    shutil.rmtree(dest_dir)

print(f"Copying {src_dir} to {dest_dir}...")
shutil.copytree(src_dir, dest_dir)

# Step 2: Relocate package folders
# Original: innertube/src/main/kotlin/moe/koiverse/archivetune/innertube
# Target: innertube/src/main/kotlin/unshoo/ianshulyadav/pixelmusic/innertube
old_package_path = os.path.join(dest_dir, "src", "main", "kotlin", "moe", "koiverse", "archivetune", "innertube")
new_package_parent = os.path.join(dest_dir, "src", "main", "kotlin", "unshoo", "ianshulyadav", "pixelmusic")
new_package_path = os.path.join(new_package_parent, "innertube")

print(f"Creating new package directory structure: {new_package_path}")
os.makedirs(new_package_parent, exist_ok=True)

# Move the innertube folder contents
shutil.move(old_package_path, new_package_path)

# Delete old empty directories under 'moe'
moe_dir = os.path.join(dest_dir, "src", "main", "kotlin", "moe")
if os.path.exists(moe_dir):
    print("Deleting old package directories...")
    shutil.rmtree(moe_dir)

# Step 3: Rebrand content recursively
# Replacements to perform:
# moe.koiverse.archivetune.innertube -> unshoo.ianshulyadav.pixelmusic.innertube
# github.com/koiverse -> github.com/ianshulyadav
# theovilardo -> ianshulyadav
# moe -> Unshoo (case-sensitive replacements)
replacements = [
    (r"moe\.koiverse\.archivetune", "unshoo.ianshulyadav.pixelmusic"),
    (r"moe/koiverse/archivetune", "unshoo/ianshulyadav/pixelmusic"),
    (r"koiverse", "ianshulyadav"),
    (r"theovilardo", "ianshulyadav"),
    (r"\bmoe\b", "Unshoo"), # match word boundary
]

print("Performing string replacements in files...")
for root, dirs, files in os.walk(dest_dir):
    for file in files:
        if file.endswith((".kt", ".kts", ".txt", ".md", ".xml", ".properties", ".gitignore")):
            filepath = os.path.join(root, file)
            with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            
            modified = content
            for pattern, repl in replacements:
                modified = re.sub(pattern, repl, modified)
                
            if modified != content:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(modified)
                print(f"Rebranded: {os.path.relpath(filepath, dest_dir)}")

print("Module copy and rebranding complete!")
