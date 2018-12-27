# RUSE

RUSE Userspace System Extensions

## Huh?

This is a fun demo/POC of using Clojure + Linux FUSE (Filesystem in
Userspace).

Currently I have a sample that maps some RESTful API data to a local
file mount.  This then allows the user to run all their native file
level commands (`ls`, `grep`, `feh`) on the directory.

## Installation

This requires lein:

https://leiningen.org/

After it is installed and 'lein' is in your $PATH, just run (in
project root):

```
lein deps
```

## Usage

Try this command, you'll be able to browse a directory sourced from
the RESTful API from the response of images at: https://dog.ceo/api/breed/dane-great/images

This would be a great way to view/edit CRUD documents over other
RESTful endpoints, or even to display database records for editing in
a user's favorite editor.

```
mkdir /tmp/dog-pics && lein run /tmp/dog-pics
```

Then in another terminal:

```
cd /tmp/dog-pics
ls
cd dane
feh dane-0.jpg # Use your CLI image editor of choice
```

or if you prefer a more GUI based approach, just open your thumbnailer
capable file explorer (or use these two small tools to do it):

```
sudo pacman -S raw-thumbnailer pcmanfm
cd /tpm/dog-pics
pcmanfm .
```


## License

Copyright © 2018 Matthew Carter <m@ahungry.com>

AGPLv3 or later
