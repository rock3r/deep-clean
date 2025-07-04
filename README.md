# deep-clean

A Kotlin script that nukes all build caches from Gradle/Android projects.
Useful when Gradle or the IDE let you down 💔

![deep-clean in action](https://user-images.githubusercontent.com/153802/41173653-ab0ae36c-6b4f-11e8-8f98-8dba4340add7.png)

🎩 h/t to [@Takhion](https://github.com/Takhion) for the original idea.

The script has been tested on macOS 🍎, but it is completely untested on Linux 🐧 and Windows 🖥️.

> [!CAUTION]
> There may be [major issues](https://github.com/rock3r/deep-clean/issues/4) on Windows/Linux when using `-n`,
> please let me know if you encounter any such issue!

**USE AT YOUR OWN RISK IN ANY CASE!**

## Running the script

`deep-clean` requires [`kotlin`](https://kotlinlang.org/docs/command-line.html) to be on your `PATH`.

If you **have kotlin** on your `PATH`, then you can download and execute the script:

```bash
$ cd /your/project/root.folder
$ [kotlin] deep-clean.main.kts [options]
```

> [!NOTE]
> On macOS and Linux the script does not need `kotlin` to be invoked, because
> it has a [shebang](https://en.wikipedia.org/wiki/Shebang_(Unix)).
>
> On Windows, you will need to explicitly specify you want to use `kotlin` to run it.

Where the options are:

```
-b --backup           Renames files and folders instead of deleting them. Implies
                      --verbose.
-d --dry-run          Don't delete anything. Useful for testing. Implies --verbose.
-i --ide-files        This also deletes IDEA/Android Studio project files (*.iml).
                      If used in conjunction with --nuke it will also delete the
                      .idea folder in the current directory.
-p --ide-preferences  ⚠️  THIS IS DANGEROUS SHIT ⚠️  Will wipe your IDE settings!
                      This deletes the GLOBAL IDEA/Android Studio preferences.
                      This option requires the --nuke option to be active too, since
                      it touches global system state.
--not-recursive       Don't recursively search sub-folders of this folder for matches.
                      The default behaviour is to look for matches in sub-directories,
                      since things like 'build' folders and '.iml' files are not all
                      found at the top level of a project directory structure. This
                      flag is useful if you know you have matches you want to keep,
                      e.g., if your code contains a package with a name like 'build'.
                      This option severely limits the effectiveness of the deep clean.
-n --nuke             ⚠️  THIS IS DANGEROUS SHIT ⚠️  Super-deep clean
                      This includes clearing out global folders, including:
                       * the global Gradle cache
                       * the global Maven artefacts
                       * the wrapper-downloaded Gradle distros
                       * the Gradle daemon data (logs, locks, etc.)
                       * the Android build cache
                      Nukes the entire thing from orbit — it's the only way to be sure.
-v --verbose          Print detailed information about all commands.
```

## Licence

```
Copyright 2025 Sebastiano Poggi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

For more information please refer to the [`LICENSE`](LICENSE) file.
