#!/bin/bash

for dir in ../services/*/; do
    libs=${dir}target/lib/*
    cp -fv ${libs} target/lib
done

cp -fv ../shell/target/lib/* target/lib
