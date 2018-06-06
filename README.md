# deep-clean
A Kotlin script that nukes all build caches from Gradle/Android projects.
Useful when Gradle or the IDE let you down 💔

🎩 h/t to [@Takhion](https://github.com/Takhion) for the original idea, and to
[@holgerbrandl](https://github.com/holgerbrandl) for KScript.

The script has been tested on macOS 🍎, but it is completely untested on
Linux 🐧 and Windows 🖥️. USE AT YOUR OWN RISK IN ANY CASE!

## Running the script

`deep-clean` requires three components to be on your `PATH`:
 * [`kotlinc`](https://kotlinlang.org/docs/tutorials/command-line.html)
 * [`kscript`](https://github.com/holgerbrandl/kscript)
 * [`mvn`](https://maven.apache.org/)

If you **have all three commands** on your `PATH`, then you can simply download
and execute the script:

```bash
$ [kscript] deep-clean [options]
```

>Note: on macOS and Linux the script does not need `kscript` to be invoked, because
>it has a [shebang](https://en.wikipedia.org/wiki/Shebang_(Unix)). On Windows, you
>will need to explicitly specify you want to use `kscript` to run it.

Where the options are:

```
-d --dry-run  Don't delete anything. Useful for testing. Implies --verbose.
-n --nuke     ⚠️  THIS IS DANGEROUS SHIT ⚠️  Super-deep clean, includes
              Android build cache, global Gradle cache, etc. Nukes the
              entire thing from orbit — it's the only way to be sure.
-v --verbose  Print detailed information about all commands.
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
$ brew install kotlin maven kscript
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