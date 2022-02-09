## kinstall

`kinstall` is an easy way to install gradle-based kotlin projects that use the `application` plugin.

## use

First, install `kinstall` itself by doing `gradle build run`. This will create a symlink in `~/bin/kinstall`.

Run `kinstall` in the root project folder of a project on which you've already run `distTar` (`build` runs it).

## what it does

`kinstall` looks for `tar` files in `build/distributions` and extracts them to `~/.kinstall`. Then it looks for scripts in `~/.kinstall/{app folder}/bin` and symlinks them to `~/bin`. It reports the symlinks it creates.