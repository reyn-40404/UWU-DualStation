from pathlib import Path
import sys
root=Path(sys.argv[1])
home=root/"app/src/main/java/com/uwu/dualstation/ui/HomeScreen.kt"
s=home.read_text(encoding="utf-8")
if "MediaLibraryDialog" not in s:
    s=s.replace("import com.uwu.dualstation.LaunchResult\n", "import com.uwu.dualstation.LaunchResult\n")
    s=s.replace('    var showConfig by remember { mutableStateOf(false) }\n', '    var showConfig by remember { mutableStateOf(false) }\n    var showMediaLibrary by remember { mutableStateOf(false) }\n')
    old='''                ComingSoonTile(
                    title = "下載中心",
                    tag = "v0.2",
                    icon = Icons.Filled.Download,
                    modifier = Modifier.weight(1f),
                )'''
    new='''                Button(
                    onClick = { showMediaLibrary = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Text("下載中心")
                }'''
    if old not in s:
        raise SystemExit("找不到下載中心原始區塊，停止套用")
    s=s.replace(old,new)
    old_end='''            onDismiss = { showConfig = false },
        )
    }
}'''
    new_end='''            onDismiss = { showConfig = false },
        )
    }
    if (showMediaLibrary) {
        MediaLibraryDialog(onDismiss = { showMediaLibrary = false })
    }
}'''
    if old_end not in s:
        raise SystemExit("找不到 HomeScreen 結尾，停止套用")
    s=s.replace(old_end,new_end,1)
    home.write_text(s,encoding="utf-8")

gradle=root/"app/build.gradle.kts"
g=gradle.read_text(encoding="utf-8")
if "androidx.documentfile:documentfile" not in g:
    g=g.replace('    implementation("androidx.core:core-ktx:1.15.0")\n','    implementation("androidx.core:core-ktx:1.15.0")\n    implementation("androidx.documentfile:documentfile:1.0.1")\n')
g=g.replace("versionCode = 1","versionCode = 2").replace('versionName = "0.1.0"','versionName = "0.2.0"')
gradle.write_text(g,encoding="utf-8")
