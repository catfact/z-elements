jumping-off point for [**z-elements**](http://moth-object.com/workshop/z-elements/) workshop.

this code consists of classes and startup scripts.

i like to install them using softlinks from the repo to one of supercollider support directories (user or system, but usually the former.)

for example, on macOS:

```
git clone git@github.com:catfact/z-elements ~/code/z-elements
cd ~/Library/Application\ Support/SuperCollider
ln -s ~/code/z-elements/classes ./Extensions/z-elements-classes
ln -s ~/code/z-elements/startup ./startup-basic.scd
```

