/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.qtf.fuse;


import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import jnr.ffi.Pointer;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.qtf.QTFConfig;
import org.polypheny.qtf.web.BatchUpdateRequest;
import org.polypheny.qtf.web.BatchUpdateRequest.Update;
import org.polypheny.qtf.web.Result;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseContext;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;

import static org.polypheny.qtf.StartUpController.getHost;
import static org.polypheny.qtf.StartUpController.getPortNbr;


@Slf4j
//see https://github.com/SerCeMan/jnr-fuse/blob/master/src/main/java/ru/serce/jnrfuse/examples/MemoryFS.java
public class ResultFS extends FuseStubFS {

    public enum Operation {
        CREATE, UNLINK, WRITE
    }


    public static class ResultDirectory extends ResultPath {

        private final HashMap<String, ResultPath> contents = new HashMap<>();
        private final Result result;
        private final int ithRow;

        private ResultDirectory( String name ) {
            super( name );
            this.result = null;
            this.ithRow = -1;
        }

        public ResultDirectory( String name, ResultDirectory parent ) {
            super( name, parent );
            this.result = null;
            this.ithRow = -1;
        }

        public ResultDirectory( ResultDirectory parent, Result result, int ithRow ) {
            super( String.valueOf( ithRow ), parent );
            this.result = result;
            this.ithRow = ithRow;
        }

        public synchronized void add( ResultPath p ) {
            contents.put( p.name, p );
            p.parent = this;
        }

        private synchronized void deleteChild( ResultPath child ) {
            contents.remove( child.name );
        }

        @Override
        protected boolean isDeleted() {
            return false;
        }

        @Override
        protected ResultPath find( String path ) {
            ResultPath findPath = super.find( path );
            if ( findPath != null && !findPath.isDeleted() ) {
                return findPath;
            }
            while ( path.startsWith( "/" ) ) {
                path = path.substring( 1 );
            }
            synchronized ( this ) {
                if ( !path.contains( "/" ) ) {
                    ResultPath out = contents.get( path );
                    if ( out != null && !out.isDeleted() ) {
                        return out;
                    } else {
                        return null;
                    }
                }
                String nextName = path.substring( 0, path.indexOf( "/" ) );
                String rest = path.substring( path.indexOf( "/" ) );
                if ( contents.containsKey( nextName ) ) {
                    ResultPath out = contents.get( nextName ).find( rest );
                    if ( out != null && !out.isDeleted() ) {
                        return out;
                    } else {
                        return null;
                    }
                }
            }
            return null;
        }

        @Override
        protected void getattr( FileStat stat, FuseContext context ) {
            stat.st_mode.set( FileStat.S_IFDIR | 0777 );
            stat.st_uid.set( context.uid.get() );
            stat.st_gid.set( context.gid.get() );
        }

        private synchronized void mkdir( String lastComponent ) {
            contents.put( lastComponent, new ResultDirectory( lastComponent, this ) );
        }

        /**
         * Create a new file in the FS.
         *
         * @param lastComponent Name of the file
         * @param generateQuery If the creation of this file will lead to a insert/update query
         */
        public synchronized void mkfile( String lastComponent, boolean generateQuery ) {
            if ( generateQuery ) {
                ResultPath existing = contents.get( lastComponent );
                if ( existing != null ) {
                    ResultFile rf = (ResultFile) existing;
                    rf.setOperation( Operation.CREATE );
                    rf.setName( lastComponent );
                } else {
                    contents.put( lastComponent, new ResultFile( lastComponent, this, true ).setOperation( Operation.CREATE ) );
                }
            } else {
                contents.put( lastComponent, new ResultFile( lastComponent, this, true ) );
            }
        }

        public synchronized void read( Pointer buf, FuseFillDir filler ) {
            for ( ResultPath p : contents.values() ) {
                if ( p.isDeleted() ) {
                    continue;
                }
                filler.apply( buf, p.name, null, 0 );
            }
        }

        protected void getUpdate( BatchUpdateRequest updateRequest ) {
            Update update = updateRequest.new Update( ithRow );
            boolean add = false;
            for ( ResultPath rp : contents.values() ) {
                if ( rp instanceof ResultFile && result.containsColumn( rp.getName() ) ) {
                    ResultFile rf = (ResultFile) rp;
                    if ( rf.createdByFS ) {
                        update.addValue( rf );
                        add = true;
                    }
                }
            }
            if ( add ) {
                updateRequest.addUpdate( update );
            }
        }
    }


    public static class ResultFile extends ResultPath {

        private ByteBuffer contents = ByteBuffer.allocate( 0 );
        @Getter
        private String dataAsString;
        private String url;
        boolean createdByFS;
        @Getter
        private Operation lastOp = Operation.CREATE;
        @Getter
        private boolean deleted = false;

        /**
         * This constructor is called by mkfile
         * (when a file is created by the FS)
         */
        private ResultFile( String name, ResultDirectory parent, boolean createdByFS ) {
            super( name, parent );
            this.createdByFS = createdByFS;
        }

        public static ResultFile ofData( String name, Object data, ResultDirectory parent ) {
            ResultFile file = new ResultFile( name + ".txt", parent, false );
            if ( data instanceof String ) {
                file.dataAsString = (String) data;
                file.contents = ByteBuffer.wrap( ((String) data).getBytes( StandardCharsets.UTF_8 ) );
            } else if ( data instanceof byte[] ) {
                file.contents = ByteBuffer.wrap( (byte[]) data );
            } else {
                String s = gson.toJson( data.toString() );
                file.dataAsString = s;
                file.contents = ByteBuffer.wrap( s.getBytes( StandardCharsets.UTF_8 ) );
            }
            return file;
        }

        public static ResultFile ofUrl( String name, String url, ResultDirectory parent ) {
            String extension = "";
            if ( url.contains( "." ) ) {
                extension = url.substring( url.lastIndexOf( "." ) );
            }
            ResultFile file = new ResultFile( name + extension, parent, false );
            file.url = url;
            //put something into contents, else the file read method will not be called
            file.contents = ByteBuffer.wrap( "loading".getBytes( StandardCharsets.UTF_8 ) );
            return file;
        }

        @Override
        protected void getattr( FileStat stat, FuseContext context ) {
            stat.st_mode.set( FileStat.S_IFREG | 0777 );
            stat.st_size.set( contents.capacity() );
            stat.st_uid.set( context.uid.get() );
            stat.st_gid.set( context.gid.get() );
        }

        private int read( Pointer buffer, long size, long offset ) {
            if ( url != null ) {
                HttpResponse<byte[]> restResponse = restRequest( url );
                if ( restResponse.isSuccess() ) {
                    contents = ByteBuffer.wrap( restResponse.getBody() );
                } else {
                    contents = ByteBuffer.allocate( 0 );
                    return 0;
                }
            }
            int bytesToRead = (int) Math.min( contents.capacity() - offset, size );
            byte[] bytesRead = new byte[bytesToRead];
            synchronized ( this ) {
                contents.position( (int) offset );
                contents.get( bytesRead, 0, bytesToRead );
                buffer.put( 0, bytesRead, 0, bytesToRead );
                contents.position( 0 ); // Rewind
            }
            return bytesToRead;
        }

        private HttpResponse<byte[]> restRequest( final String fileUrl ) {
            return Unirest.get( String.format( "http://%s:%s/getFile/%s", getHost(), getPortNbr(), fileUrl ) ).asBytes();
        }

        private synchronized void truncate( long size ) {
            if ( size < contents.capacity() ) {
                // Need to create a new, smaller buffer
                ByteBuffer newContents = ByteBuffer.allocate( (int) size );
                byte[] bytesRead = new byte[(int) size];
                contents.get( bytesRead );
                newContents.put( bytesRead );
                contents = newContents;
            }
        }

        private int write( Pointer buffer, long bufSize, long writeOffset ) {
            int maxWriteIndex = (int) (writeOffset + bufSize);
            byte[] bytesToWrite = new byte[(int) bufSize];
            synchronized ( this ) {
                if ( maxWriteIndex > contents.capacity() ) {
                    // Need to create a new, larger buffer
                    ByteBuffer newContents = ByteBuffer.allocate( maxWriteIndex );
                    newContents.put( contents );
                    contents = newContents;
                }
                buffer.get( 0, bytesToWrite, 0, (int) bufSize );
                contents.position( (int) writeOffset );
                contents.put( bytesToWrite );
                contents.position( 0 ); // Rewind
            }
            return (int) bufSize;
        }

        protected ResultFile setOperation( Operation operation ) {
            log.debug( name + ": set op: " + operation );
            this.lastOp = operation;
            this.deleted = operation == Operation.UNLINK;
            this.createdByFS = true;
            this.url = null;
            return this;
        }
    }


    public static abstract class ResultPath {

        @Getter
        @Setter
        protected String name;
        @Getter
        protected ResultDirectory parent;

        private ResultPath( String name ) {
            this( name, null );
        }

        private ResultPath( String name, ResultDirectory parent ) {
            this.name = name;
            this.parent = parent;
        }

        public String getPath() {
            String path = name;
            if ( parent != null ) {
                path = parent.getPath() + "/" + path;
                return path;
            } else {
                return "";
            }
        }

        private synchronized void delete() {
            if ( parent != null ) {
                parent.deleteChild( this );
                parent = null;
            }
        }

        private synchronized void pseudoDelete() {
            if ( this instanceof ResultFile ) {
                ResultFile rf = (ResultFile) this;
                rf.setOperation( Operation.UNLINK );
            } else if ( parent != null ) {
                parent.deleteChild( this );
                parent = null;
            }
        }

        protected ResultPath find( String path ) {
            while ( path.startsWith( "/" ) ) {
                path = path.substring( 1 );
            }
            if ( path.equals( name ) || path.isEmpty() ) {
                return this;
            }
            return null;
        }

        protected abstract boolean isDeleted();

        protected abstract void getattr( FileStat stat, FuseContext context );

        private void rename( String newName ) {
            while ( newName.startsWith( "/" ) ) {
                newName = newName.substring( 1 );
            }
            if ( this instanceof ResultFile ) {
                ((ResultFile) this).setOperation( Operation.WRITE );
                ((ResultFile) this).createdByFS = true;
            }
            name = newName;
        }
    }


    static Gson gson = new Gson();
    @Getter
    private final ResultDirectory rootDirectory;
    @Setter
    @Getter
    private Result result;

    public ResultFS() {
        this.rootDirectory = new ResultDirectory( "root" );
    }

    public void add( ResultPath path ) {
        this.rootDirectory.add( path );
    }

    @Override
    public int create( String path, @mode_t long mode, FuseFileInfo fi ) {
        log.debug( "create " + path);
        if ( getPath( path ) != null ) {
            return -ErrorCodes.EEXIST();
        }
        ResultPath parent = getParentPath( path );
        if ( parent instanceof ResultDirectory ) {
            String lastComponent = getLastComponent( path );
            ((ResultDirectory) parent).mkfile( lastComponent, result != null && result.containsColumn( lastComponent ) );
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }


    @Override
    public int getattr( String path, FileStat stat ) {
        ResultPath p = getPath( path );
        if ( p != null ) {
            p.getattr( stat, getContext() );
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int fgetattr( String path, FileStat stbuf, FuseFileInfo fi ) {
        return getattr( path, stbuf );
    }

    private String getLastComponent( String path ) {
        while ( path.substring( path.length() - 1 ).equals( "/" ) ) {
            path = path.substring( 0, path.length() - 1 );
        }
        if ( path.isEmpty() ) {
            return "";
        }
        return path.substring( path.lastIndexOf( "/" ) + 1 );
    }

    private ResultPath getParentPath( String path ) {
        return rootDirectory.find( path.substring( 0, path.lastIndexOf( "/" ) ) );
    }

    private ResultPath getPath( String path ) {
        return rootDirectory.find( path );
    }


    @Override
    public int mkdir( String path, @mode_t long mode ) {
        if ( getPath( path ) != null ) {
            return -ErrorCodes.EEXIST();
        }
        ResultPath parent = getParentPath( path );
        if ( parent instanceof ResultDirectory ) {
            ((ResultDirectory) parent).mkdir( getLastComponent( path ) );
            return 0;
        }
        return -ErrorCodes.ENOENT();
    }


    @Override
    public int read( String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi ) {
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(p instanceof ResultFile) ) {
            return -ErrorCodes.EISDIR();
        }
        return ((ResultFile) p).read( buf, size, offset );
    }

    @Override
    public int readdir( String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi ) {
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(p instanceof ResultDirectory) ) {
            return -ErrorCodes.ENOTDIR();
        }
        filter.apply( buf, ".", null, 0 );
        filter.apply( buf, "..", null, 0 );
        ((ResultDirectory) p).read( buf, filter );
        return 0;
    }

    @Override
    public int statfs( String path, Statvfs stbuf ) {
        final int capacityGB = QTFConfig.getFuseCapacityGB();
        stbuf.f_bsize.set( 1024 );
        stbuf.f_frsize.set( 1024 );// fs block size
        stbuf.f_blocks.set( 1024 * 1024 * capacityGB );// total data blocks in file system
        stbuf.f_bfree.set( 1024 * 1024 * capacityGB );// free blocks in fs
        stbuf.f_bavail.set( 1024 * 1024 * capacityGB );//free blocks for non-root
        stbuf.f_files.set( 1024 );//total file nodes
        stbuf.f_ffree.set( 1024 );//free file nodes
        stbuf.f_favail.set( 1024 );//free inodes for non-root
        stbuf.f_unused.set( 1024 * 1024 * capacityGB );

        return super.statfs( path, stbuf );
    }

    @Override
    public int rename( String path, String newName ) {
        log.debug( String.format( "rename %s to %s\n", path, newName ));
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        ResultPath newParent = getParentPath( newName );
        if ( newParent == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(newParent instanceof ResultDirectory) ) {
            return -ErrorCodes.ENOTDIR();
        }
        if ( result.containsColumn( p.name ) ) {
            p.pseudoDelete();
        } else {
            p.delete();
        }
        p.rename( newName.substring( newName.lastIndexOf( "/" ) ) );
        ((ResultDirectory) newParent).add( p );
        return 0;
    }

    @Override
    public int rmdir( String path ) {
        log.debug( "rmdir " + path );
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(p instanceof ResultDirectory) ) {
            return -ErrorCodes.ENOTDIR();
        }
        p.delete();
        return 0;
    }

    @Override
    public int truncate( String path, long offset ) {
        log.debug( "truncate " + path);
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(p instanceof ResultFile) ) {
            return -ErrorCodes.EISDIR();
        }
        ((ResultFile) p).truncate( offset );
        return 0;
    }

    @Override
    public int unlink( String path ) {
        log.debug( "unlink " + path );
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( result.containsColumn( p.name ) ) {
            p.pseudoDelete();
        } else {
            p.delete();
        }
        return 0;
    }

    @Override
    public int open( String path, FuseFileInfo fi ) {
        return 0;
    }

    @Override
    public int write( String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi ) {
        log.debug( "write " + path );
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(p instanceof ResultFile) ) {
            return -ErrorCodes.EISDIR();
        }
        ResultFile rf = (ResultFile) p;
        rf.setOperation( Operation.WRITE );
        return rf.write( buf, size, offset );
    }

    /**
     * This method is needed for the OS X `cp` command (copy via terminal)
     */
    @Override
    public int chmod( String path, long mode ) {
        return super.chmod( path, mode );
    }

    public void reset() {
        rootDirectory.contents.clear();
    }

    /**
     * Determine changes in FS and create corresponding SQL statements
     *
     * @return BatchUpdateRequest
     */
    public BatchUpdateRequest getBatchUpdateRequest() {
        if ( result.table == null ) {
            return null;
        }
        BatchUpdateRequest updateRequest = new BatchUpdateRequest( result );
        for ( ResultPath rp : rootDirectory.contents.values() ) {
            if ( rp instanceof ResultDirectory && !rp.isDeleted() ) {
                ResultDirectory rd = (ResultDirectory) rp;
                rd.getUpdate( updateRequest );
            }
        }
        return updateRequest;
    }
}
