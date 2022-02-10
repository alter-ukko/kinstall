## kinstall

`kinstall` is an easy way to install gradle-based command-line kotlin projects that use the `application` plugin.

## use

First, install `kinstall` itself by doing `gradle build run`. This will create a symlink at `~/bin/kinstall`.

Run `kinstall` in the root project folder of a project on which you've already run `distTar` (`build` runs it).

You can add and remove multiple versions of a project, and list the projects you have installed (the version in current use will be marked with an asterisk).

Here's the usage for the `kinstall` command:

```
Usage:
    kinstall [options]
    kinstall --version
    kinstall -h | --help

Options:
    -l --list                                   list installs
    -r <name:version> --remove=<name:version>   remove an install
    -u <name:version> --use=<name:version>      use a specific version of an install
```

## what it does

`kinstall` looks for `tar` files in `build/distributions` and extracts them to `~/.kinstall/{project}/{version}`. The current version of a project that's in use is symlinked to `~/.kinstall/{project}/_current`. When making a particular version current, `kinstall` looks for scripts in `~/.kinstall/{project}/_current/bin` and symlinks them to `~/bin`. It reports the symlinks it creates.

It gets the project's name and version by capturing and parsing the output of `gradle properties --console=plain -q`.

## what it doesn't do

* Handle parent/sub-project projects (it expects a single project).
* Make sure your build is current.
* Handle non-gradle ways of creating tarballs (it expects a `tar` file in `build/distributions` that has a `bin` subdirectory).
* Attempt to correct for any manual changes to the files/symlinks in `~/.kinstall` or `~/bin`.
* Handle maven projects.
