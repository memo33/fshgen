 fshgen 0.1.2
==============

A command line tool for converting FSH files back and forth. Among the features
are:

- fuzzy matching on the file names to extract the IID
- all FSH formats (aside from 8-bit palette), including embedded mipmaps and
  (export-only) multi-FSHs
- IID shifts and mipmap creation
- on-the-fly S3D-darkening and brightening as per the color curves


 Download
----------

Download at https://www.dropbox.com/sh/dx40oit1lgdey1v/AADAIzFzMKVw6PLQI83nfQLQa?dl=0


 Requirements
--------------

This program requires Java 1.6 or later.


 Installation
--------------

There are many ways to install this program. One way is to extract the jar file
contained in the zip archive to any permanent location and (assuming a Unix
shell) add the following to your (bash) profile (with the path replaced):

    alias fshgen="java -jar -Djava.awt.headless=true /path/to/fshgenjarfile.jar"

That way, you can use `fshgen` as a command. (The _headless_ option tells AWT
that the program is executed from the console, so that no GUI thread needs to be
started.)


 Usage
-------

There are two `fshgen` commands: `import` (used for converting png or bmp to FSH
as DBPF dat file) and `export` (used to export FSH files from a dat file to
png). See the examples below. To print the usage text, use `fshgen --help`.


 Examples
----------

The most basic import command is as follows:

    fshgen import -o foobar.dat 12345678.png

It takes the one png file, converts it to FSH and saves it in the output file
foobar.dat. You can specify multiple image files, too, by either listing them
(space-separated) or using wildcards, such as:

    fshgen import -o foobar.dat 5*.png

This would convert all png files starting with '5' in the current directory. If
no image file is specified, the file names will be read from stdin. This is
useful to interact with other programs or if you have lots of files to import
(the number of parameters in the above style is limited to about 1000). For
example:

    find . -name "5*.png" | fshgen import -o foobar.dat

The `find` command lists all the files matching the pattern in the current
directorie _and_ subdirectories, which are passed to `fshgen` using the pipe
operator `|`.

There are a number of options you can specify, the most important of which are:
`-f` to force overwriting existing output files, or `-a` to append to existing
files. Use `-m` to create separate mipmaps and `-e` for embedded mipmaps. Use
`-D` to create darken the images on-the-fly for use on S3D files, or `-B` to
brighten previously darkened textures. Use `-i` to offset the IIDs.

The IIDs will be extracted from the file names, if they contain a sequence of 8
hex digits. If multiple such sequences exist, the last one will be chosen (so as
to allow for fully qualified TGIs in file names – the GID won't be taken into
account, though, but can be changed by the `--gid` option). Using the
`--alpha-separate` flag, it is possible to match separate colour and alpha
files. Files with names such that the IID is followed by space/underscore
followed by a/alpha will be considered to be alpha files (for example
`12345678_a.png` or `12345678_alpha.png`).

It is possible, using `--attach-filename`, to add the file name to the FSH file,
so that (once there is a suitable viewer) the file names can be displayed as a
descriptive text. Many of the Maxis FSH files have such an attachment.

Therefore, a full import call might look like:

    fshgen import -ma -i 4 --attach-filename -o foobar.dat 5*.png

The export command is used as follows:

    fshgen export foobar.dat

This would export all the FSH files from the file (multiple files can be
specified, too, as with import). It is possible to specify a different output
directory than the current using `-o`. Force overwriting of existing png files
using `-f`. You can also specify a (Java-style) regular expression pattern using
`-p` to match against the (case-insensitive) IID, such as

    fshgen export -p '.*[49ef]' foobar.dat

to export only those FSH files with an 8th digit of 4, 9, e or f, or

    fshgen export -p '57.*' foobar.dat

to export all FSH files with IIDs starting with 57.

Make sure to check `fshgen --help` for all the options.


 Slicing
---------

If you pass the `--slice` or `-s` option to the import command, an extensive
matching process is started that doesn't only allow for automatically slicing
images into pieces, but also supports composition of multiple layers and
automatic generation of sidewalk textures. The rules for this are as follows.

The algorithm extracts IIDs from every file name. Consider for example:

    0x12340000-2-1_+0x100_0_+200-1-0_56780000_some_more_text.png

The file name contains absolute IDs, relative IDs, and Zeroes. Absolute IDs
consist of 8 hex digits (optionally prefixed by `0x`), optionally suffixed by
rotation and flip (`-2-1`, defaults to `-0-0`). Relative IDs start with `+`
or `-` and indicate the offset (pos. or neg.) in relation to the last absolute ID.
If no rotation and flip is given, the orientation of the last absolute ID is assumed.
Zero IDs (as seen between +0x100 and +200) indicate tiles to be ignored during
the slicing process. The IDs can occur anywhere in the name, though they need
to be properly separated from the rest of the text by underscore or blank.

The order of slices is first left to right and then top to bottom (logical
reading order). If the number of subimages and the number of extracted IDs are
different, trailing instances are ignored.

The IDs extracted from the above example would be:

    0x12340000-2-1
    0x12340100-2-1
    0
    0x12340200-1-0
    0x56780000-0-0

There can be multiple instances of subimages that map to the same ID (even in
the same image file), in which case all of the subimages of a single ID are
overlayed, allowing for composition of layers. Thus, a file named
`12340000_+0.png` is sliced into two pieces which end up as layers of the
same FSH file. The layers are processed in alphabetical file name order, such
that the top most layer needs to have an alphabetically higher name than
previous occurences of a specific ID (lowercase).

If one of the IDs(-Rot—Flip) (preferrably the last one) is suffixed by
`_a` or `_alpha`, the entire image is considered to be an alpha mask
(optional). If one of the IDs is followed by `_b` or `_balpha`, the entire
image is considered to be the alpha mask for sidewalk textures (optional).
If and only if a sidewalk alpha mask is found, sidewalk textures are generated.


 Compiling
-----------

Compiling this program requires adding resources from previous version.
Use `sbt dist` to create a full distribution zip file. Alternatively, use
`sbt pack` to create binaries.


 Contact and Support
---------------------

Contact the author (memo) at
[SC4Devotion.com](http://sc4devotion.com).


 License
---------

This program is released under the MIT license (see included license file).
