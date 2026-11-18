#!/bin/zsh
zparseopts -D -E -- nc=flag_nc -no-checks=flag_nc

NO_CHECKS=0
(( $#flag_nc )) && NO_CHECKS=1

git pull

if [[ $NO_CHECKS -eq 1 ]]; then
    echo "skipping checks"
else
    gh pr checks --watch -i 5
fi

if [[ "$?" == 0 ]]; then
    gh pr merge -s -d --auto
    git remote prune origin
    termux-notification -c "PR merged"
else
    echo "Checks failed"
    termux-notification -c "Checks failed"
    exit 1
fi
