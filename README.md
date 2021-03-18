# Query-to-File

The Query-to-File tool lets you materialize the result of a query as a set of files in a folder. In order to use the tool, you need to install a FUSE library on your computer. See this [installation guide](https://github.com/SerCeMan/jnr-fuse/blob/master/INSTALLATION.md) for more information. After setting up the FUSE library, find the library file and set it in the config.properties. The config.properties file provides some examples where you could find the library file. In the config file, you can set up the Polypheny-DB host and port as well.

All files that are materialized in the mounted folder are kept in memory. Multimedia files are initially empty. As you open a multimedia file, the content will be fetched from Polypheny-DB, loaded into memory and then returned. This can lead to waiting times, and your file explorer might not react during this moment.

The query-to-file tool provides two ways to submit queries:
The console lets you submit any kind of query and the result of SELECT queries will be materialized into the mounted folder. These files can easily be copied to another file system.

The second way is by providing a schema and table name (schema.table). When submitting this request, the whole table will be fetched and materialized. The files can be edited and missing column files can be added (make sure to use the correct filename, the extension does not matter.). When clicking on the _commit_ button, the changes will be derived and a UPDATE statement is generated and executed. The support for DELETE and INSERT queries via the filesystem is not yet existent.

## Acknowledgements

Query-to-File uses icons made by [dmitri13](https://www.flaticon.com/authors/dmitri13) from [flaticon.com](https://www.flaticon.com/)