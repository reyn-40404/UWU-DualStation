from pathlib import Path
import re
import sys

root = Path(sys.argv[1])

# Home screen: connect the local media library entry to the v0.3 dialog.
home = root / "app/src/main/java/com/uwu/dualstation/ui/HomeScreen.kt"
s = home.read_text(encoding="utf-8")
if "MediaLibraryDialog" not in s:
    s = s.replace(
        '    var showConfig by remember { mutableStateOf(false) }\n',
        '    var showConfig by remember { mutableStateOf(false) }\n    var showMediaLibrary by remember { mutableStateOf(false) }\n'
    )
    old = '''                ComingSoonTile(
                    title = "下載中心",
                    tag = "v0.2",
                    icon = Icons.Filled.Download,
                    modifier = Modifier.weight(1f),
                )'''
    new = '''                Button(
                    onClick = { showMediaLibrary = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text("本機影片")
                }'''
    if old not in s:
        raise SystemExit("找不到下載中心原始區塊，停止套用")
    s = s.replace(old, new)
    old_end = '''            onDismiss = { showConfig = false },
        )
    }
}'''
    new_end = '''            onDismiss = { showConfig = false },
        )
    }
    if (showMediaLibrary) {
        MediaLibraryDialog(onDismiss = { showMediaLibrary = false })
    }
}'''
    if old_end not in s:
        raise SystemExit("找不到 HomeScreen 結尾，停止套用")
    s = s.replace(old_end, new_end, 1)
    home.write_text(s, encoding="utf-8")

# Dependencies and app version.
gradle = root / "app/build.gradle.kts"
g = gradle.read_text(encoding="utf-8")
if "androidx.documentfile:documentfile" not in g:
    g = g.replace(
        '    implementation("androidx.core:core-ktx:1.15.0")\n',
        '    implementation("androidx.core:core-ktx:1.15.0")\n    implementation("androidx.documentfile:documentfile:1.0.1")\n'
    )
if "org.videolan.android:libvlc-all" not in g:
    g = g.replace(
        '    implementation("androidx.core:core-ktx:1.15.0")',
        '    implementation("androidx.core:core-ktx:1.15.0")\n    implementation("org.videolan.android:libvlc-all:3.6.0")'
    )
g = re.sub(r'versionCode\s*=\s*\\d+', 'versionCode = 4', g)
g = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.4.0"', g)
gradle.write_text(g, encoding="utf-8")

# Register the in-app VLC activity.
manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
if ".LocalPlayerActivity" not in m:
    m = m.replace(
        '    </application>',
        '''        <activity
            android:name=".LocalPlayerActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize|keyboardHidden" />
    </application>'''
    )
manifest.write_text(m, encoding="utf-8")
