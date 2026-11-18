#!/bin/zsh
set -e

die() {
    printf "%s\n" $1
    exit 1
}
[[ -n "$1" ]] || die "no version specified"
[[ "$1" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Release tags must use vMAJOR.MINOR.PATCH; got $1"
git pull
git tag --annotate "$1" -m "Release $1"
git push --tags

