#!/bin/sh -x

set -e

echo "Initialize Workspace"

git config --global url."https://github.com/".insteadOf 'git@github.com:'
wit --repo-path $PWD/.. init workspace -a soc-freedom-sifive
cd workspace/

wake --init .

echo "Compile Scala"

wake -v compileScalaModule e300ScalaModule
wake -v compileScalaModule u500ScalaModule

echo "Generate Verilog"

wake -v makeRTL e300ArtyDUTPlan
wake -v makeRTL u500VC707DUTPlan

