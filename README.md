# Query-to-File

_Polypheny Query-to-File_ lets you materialize the result of an arbitrary query as a file system (set of files in a folder). All files that are materialized in the mounted folder are kept in memory. Multimedia files are initially empty. As you open a multimedia file, the content will be fetched from Polypheny-DB, loaded into memory and then returned. This can lead to waiting times, and your file explorer might not react during this moment.

In addition to executing and materializing an arbitrary query, it is also possible to provide a schema and table name (`schema.table`). The whole table will then be fetched and materialized. The files can be opened, edited. It is also possible to copy the files to a different location. Missing column files can be added (make sure to use the correct filename, the extension does not matter.).

Query-to-File supports transactions. All changes (edited files) are executed in one transaction by clicking the  _commit_ button. Uncommitted changes are lost when executing another query.

## Setup

In order to use the tool, you need to have FUSE installed on your computer. See this [installation guide](https://github.com/SerCeMan/jnr-fuse/blob/master/INSTALLATION.md) for more information. Furthermore, you need to have Java version 11 or higher installed on your system.

After setting up the FUSE library, configure the path to the fuse binary in the `config.properties` file. The file already contains some typical locations for different operating systems. In the config file, you can also configure the hostname and port of the Polypheny-DB instance.

## Limitations

This software is still considered beta. There are still some major limitations:

* Windows is not yet supported (macOS and Linux are working fine). See [issue](https://github.com/polypheny/Polypheny-DB/issues/297).
* No support for DELETE and INSERT operations. See [issue](https://github.com/polypheny/Polypheny-DB/issues/298).

## Roadmap

See the [open issues](https://github.com/polypheny/Polypheny-DB/labels/A-qtf) for a list of proposed features (and known issues).

## Contributing

We highly welcome your contributions. If you would like to contribute, please fork the repository and submit your changes as a pull request. Please consult our [Admin Repository](https://github.com/polypheny/Admin) and our [Website](https://polypheny.org) for guidelines and additional information.

Please note that we have a [code of conduct](https://github.com/polypheny/Admin/blob/master/CODE_OF_CONDUCT.md). Please follow it in all your interactions with the project.

## Credits

_Polypheny Query-to-File_ builds upon the great work of several other open source projects:

* [Apache Commons](http://commons.apache.org/): A bunch of useful Java utility classes.
* [GSON](https://github.com/google/gson): Convert Java Objects into their JSON representation and vice versa.
* [Java-WebSocket](http://tootallnate.github.io/Java-WebSocket/): WebSocket server and client implementation for Java.
* [JNR Fuse](https://github.com/SerCeMan/jnr-fuse): A FUSE implementation in java using Java Native Runtime (JNR).
* [Log4j](https://logging.apache.org/log4j/2.x/): Fast and flexible logging framework for Java.
* [OpenJFX](https://openjfx.io/): A framework for creating desktop applications.
* [Project Lombok](https://projectlombok.org/): A library providing compiler annotations for tedious tasks.
* [SLF4J](http://www.slf4j.org/): Provides a logging API by means of a facade pattern.
* [Unirest](http://kong.github.io/unirest-java/): A lightweight HTTP client library.

Query-to-File uses icons made by [dmitri13](https://www.flaticon.com/authors/dmitri13) from [flaticon.com](https://www.flaticon.com/).

## License

The Apache 2.0 License