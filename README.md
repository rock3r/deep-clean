# deep-clean
A Kotlin script that nukes all build caches from Gradle/Android projects.
Useful when Gradle or the IDE let you down 💔

![deep-clean in action](https://user-images.githubusercontent.com/153802/41173653-ab0ae36c-6b4f-11e8-8f98-8dba4340add7.png)

🎩 h/t to [@Takhion](https://github.com/Takhion) for the original idea, and to
[@holgerbrandl](https://github.com/holgerbrandl) for KScript.

The script has been tested on macOS 🍎, but it is completely untested on
Linux 🐧 and Windows 🖥️.

**USE AT YOUR OWN RISK IN ANY CASE!**

## Running the script

`deep-clean` requires three components to be on your `PATH`:
 * [`kotlinc`](https://kotlinlang.org/docs/tutorials/command-line.html)
 * [`kscript`](https://github.com/holgerbrandl/kscript)
 * [`mvn`](https://maven.apache.org/)

If you **have all three commands** on your `PATH`, then you can simply download
and execute the script:

```bash
$ [kscript] deep-clean.kts [options]
```

>Note: on macOS and Linux the script does not need `kscript` to be invoked, because
>it has a [shebang](https://en.wikipedia.org/wiki/Shebang_(Unix)). On Windows, you
>will need to explicitly specify you want to use `kscript` to run it.

Where the options are:

```
-d --dry-run     Don't delete anything. Useful for testing. Implies --verbose.
-b --backup      Renames files and folders instead of deleting them. Implies
                 --verbose.
-i --ide         This also deletes IDEA/Android Studio project files (*.iml).
                 If used in conjunction with --nuke it will also delete the
                 .idea folder in the current directory.
-n --nuke        ⚠️  THIS IS DANGEROUS SHIT ⚠️  Super-deep clean
                 This includes clearing out global folders, including:
                  * the global Gradle cache
                  * the wrapper-downloaded Gradle distros
                  * the Gradle daemon data (logs, locks, etc.)
                  * the Android build cache
                 Nukes the entire thing from orbit — it's the only way to be sure.
--not-recursive  Don't recursively search sub-folders of this folder for matches.
                 The default behaviour is to look for matches in sub-directories,
                 since things like 'build' folders and '.iml' files are not all
                 found at the top level of a project directory structure. This
                 flag is useful if you know you have matches you want to keep,
                 e.g., if your code contains a package with a name like 'build'.
                 This option severely limits the effectiveness of the deep clean.
-v --verbose     Print detailed information about all commands.
```

If you **DON'T have all three commands** on your `PATH`, then read on to the next
section to install them.

## Installing the script dependencies

To make the script run, we'll first need to install all the required dependencies.
All dependencies are available on [SDKMan!](https://sdkman.io/) (Windows, Linux, macOS)
and on [Homebrew](https://brew.sh/) (macOS only).

**Windows, Linux:**

```bash
$ sdk install kotlin
$ sdk install maven
$ sdk install kscript
```

**macOS:**

```bash
$ brew install kotlin maven holgerbrandl/tap/kscript
```

## Licence

```
Copyright 2018 Sebastiano Poggi

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
