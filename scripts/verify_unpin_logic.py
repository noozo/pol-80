#!/usr/bin/env python3
"""Static audit: long-press outside edit must never call unpin/togglePin."""
from pathlib import Path
import re, sys
src = Path('/workspace/minimal-launcher/app/src/main/java/com/bare/launcher/MainActivity.kt').read_text()
fails = []

# Every onLongClick body in home grid path
# Find HomePageContent AppGridItem onLongClick
m = re.search(
    r'AppGridItem\(\s*app = app,\s*badge = if \(editMode\).*?onLongClick = \{(.*?)\},\s*onBadgeClick',
    src, re.S)
if not m:
    fails.append('home AppGridItem onLongClick block not found')
else:
    body = m.group(1)
    for bad in ('onUnpin', 'togglePin', 'removePin'):
        # allow in comments
        for line in body.splitlines():
            if bad in line and not line.strip().startswith('//'):
                fails.append(f'onLongClick calls {bad}: {line.strip()}')
    if 'onEnterEditMode' not in body:
        fails.append('onLongClick does not enter edit mode')

# onBadgeClick must gate on editMode
if 'onBadgeClick = if (editMode && badgeArmed)' not in src and 'onBadgeClick = if (editMode)' not in src:
    fails.append('onBadgeClick not gated on editMode')

# badgeArmed delay present
if 'badgeArmed' not in src:
    fails.append('badgeArmed race guard missing')

# AppGridItem: when onBadgeClick set, no combinedClickable long-press unpin path
if 'onBadgeClick != null -> Modifier' not in src:
    fails.append('edit-mode disables combinedClickable')

if fails:
    print('FAIL')
    for f in fails: print(' -', f)
    sys.exit(1)
print('PASS static audit: long-press never unpins; badge-only unpin; badgeArmed race guard present')
