#!/bin/sh

filename='../../vocab'
echo 'How many words are there?'
wc -l ${filename}

CHARS=`cat ${filename} | tr -d '\r' | tr ' ' '\n' | tr -s '\n'`
UNIQCHARS=`echo "$CHARS" | sort -u`

echo 'How many distinct characters are there?'
echo "$UNIQCHARS" | wc -l
echo "$UNIQCHARS" | tr -d '\n'
echo

echo 'How many character occurrences are there?'
echo "$CHARS" | wc -l