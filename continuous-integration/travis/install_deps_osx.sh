#!/bin/sh -x

echo "Installing Wake"

mkdir install_wake
cd install_wake
git clone https://github.com/sifive/wake.git
git -C wake checkout a539d6700afd02dd0f83e4c887f98650e7bdebb1 # commit of mac os fix
make -C wake
export PATH=$PATH:$PWD/wake/bin
cd ..

echo "Installing Wit"

git clone --branch=v0.10.1 --depth=1 https://github.com/sifive/wit.git
export PATH=$PATH:$PWD/wit
