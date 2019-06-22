# soc-freedom-sifive
e300 and u500 devkits

# Initializing the workspace
```
wit init workspace -a git@github.com:sifive/soc-freedom-sifive.git
cd workspcae
wake --init .
```

# Compiling scala
for e300
```
wake -v compileScalaModule e300ScalaModule
```

for u500
```
wake -v compileScalaModule u500ScalaModule
```

# Generating verilog
for an E300 targeting an Arty board
```
wake -v makeRTL e300ArtyDUTPlan
```

for a U500 targeting a VC707 bard
```
wake -v makeRTL u500VC707DUTPlan
```
