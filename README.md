# swish-to-briljant

A program which takes a excel based report on sales from Swish and
converts in to a CSV file ready for consumption by Briljant. Through
some heuristics specified in settings.edn all transactions are also
classified as to which kredit account they belong to.

## Installation

The source code is available from https://github.com/LinkopingsStudentspex/swish-to-briljant

## Building

After downloading the source code either with git or by downloading a
zip-file and extracting it, move your terminal to that folder with
`cd`. Then run the following command to build a standalone version of
the program.

    $ lein uberjar

It can then be run with:

    $ target/uberjar/swish-to-briljant-0.1.0-SNAPSHOT-standalone.jar in/Swishrapport.xls

where in/Swishrapport.xls is the path to the Swish report you have
aquired from your bank.

## Usage

Once compiled this application may be run

    $ java -jar swish-to-briljant-0.1.0-standalone.jar [args]


## License

Copyright © 2019 Christian Luckey
