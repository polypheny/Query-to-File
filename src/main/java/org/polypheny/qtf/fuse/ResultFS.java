/*
 * Copyright 2019-2020 The Polypheny Project
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
import org.apache.commons.lang3.SystemUtils;
import org.polypheny.qtf.QTFConfig;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseContext;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;


//see https://github.com/SerCeMan/jnr-fuse/blob/master/src/main/java/ru/serce/jnrfuse/examples/MemoryFS.java
public class ResultFS extends FuseStubFS {

    public static class ResultDirectory extends ResultPath {

        private final HashMap<String, ResultPath> contents = new HashMap<>();

        private ResultDirectory( String name ) {
            super( name );
        }

        public ResultDirectory( String name, ResultDirectory parent ) {
            super( name, parent );
        }

        public synchronized void add( ResultPath p ) {
            contents.put( p.name, p );
            p.parent = this;
        }

        private synchronized void deleteChild( ResultPath child ) {
            contents.remove( child.name );
        }

        @Override
        protected ResultPath find( String path ) {
            if ( super.find( path ) != null ) {
                return super.find( path );
            }
            while ( path.startsWith( "/" ) ) {
                path = path.substring( 1 );
            }
            synchronized ( this ) {
                if ( !path.contains( "/" ) ) {
                    return contents.get( path );
                }
                String nextName = path.substring( 0, path.indexOf( "/" ) );
                String rest = path.substring( path.indexOf( "/" ) );
                if ( contents.containsKey( nextName ) ) {
                    return contents.get( nextName ).find( rest );
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

        public synchronized void mkfile( String lastComponent ) {
            contents.put( lastComponent, new ResultFile( lastComponent, this ) );
        }

        public synchronized void read( Pointer buf, FuseFillDir filler ) {
            for ( ResultPath p : contents.values() ) {
                filler.apply( buf, p.name, null, 0 );
            }
        }
    }


    public static class ResultFile extends ResultPath {

        private ByteBuffer contents = ByteBuffer.allocate( 0 );
        private final String name;
        private Object data;
        private String url;

        private ResultFile( String name ) {
            super( name );
            this.name = name;
        }

        private ResultFile( String name, ResultDirectory parent ) {
            super( name, parent );
            this.name = name;
        }

        public static ResultFile ofData( String name, Object data ) {
            ResultFile file = new ResultFile( name + ".txt" );
            file.data = data;
            if ( data instanceof String ) {
                file.contents = ByteBuffer.wrap( ((String) data).getBytes( StandardCharsets.UTF_8 ) );
            } else if ( data instanceof byte[] ) {
                file.contents = ByteBuffer.wrap( (byte[]) data );
            } else {
                String s = gson.toJson( data.toString() );
                file.contents = ByteBuffer.wrap( s.getBytes( StandardCharsets.UTF_8 ) );
            }
            return file;
        }

        public static ResultFile ofUrl( String name, String url ) {
            String extension = "";
            if ( url.contains( "." ) ) {
                extension = url.substring( url.lastIndexOf( "." ) );
            }
            ResultFile file = new ResultFile( name + extension );
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
            return Unirest.get( QTFConfig.getFileUrl( fileUrl ) ).asBytes();
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
    }


    public static abstract class ResultPath {

        private String name;
        private ResultDirectory parent;

        private ResultPath( String name ) {
            this( name, null );
        }

        private ResultPath( String name, ResultDirectory parent ) {
            this.name = name;
            this.parent = parent;
        }

        private synchronized void delete() {
            if ( parent != null ) {
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

        protected abstract void getattr( FileStat stat, FuseContext context );

        private void rename( String newName ) {
            while ( newName.startsWith( "/" ) ) {
                newName = newName.substring( 1 );
            }
            name = newName;
        }
    }


    static Gson gson = new Gson();

    @Getter
    private final ResultDirectory rootDirectory;

    public ResultFS() {
        this.rootDirectory = new ResultDirectory( "root" );
    }

    public void add( ResultPath path ) {
        this.rootDirectory.add( path );
    }

    @Override
    public int create( String path, @mode_t long mode, FuseFileInfo fi ) {
        if ( getPath( path ) != null ) {
            return -ErrorCodes.EEXIST();
        }
        ResultPath parent = getParentPath( path );
        if ( parent instanceof ResultDirectory ) {
            ((ResultDirectory) parent).mkfile( getLastComponent( path ) );
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
        if ( SystemUtils.IS_OS_WINDOWS ) {
            // statfs needs to be implemented on Windows in order to allow for copying
            // data from other devices because winfsp calculates the volume size based
            // on the statvfs call.
            // see https://github.com/billziss-gh/winfsp/blob/14e6b402fe3360fdebcc78868de8df27622b565f/src/dll/fuse/fuse_intf.c#L654
            if ( "/".equals( path ) ) {
                stbuf.f_blocks.set( 1024 * 1024 ); // total data blocks in file system
                stbuf.f_frsize.set( 1024 );        // fs block size
                stbuf.f_bfree.set( 1024 * 1024 );  // free blocks in fs
            }
        }
        return super.statfs( path, stbuf );
    }

    @Override
    public int rename( String path, String newName ) {
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
        p.delete();
        p.rename( newName.substring( newName.lastIndexOf( "/" ) ) );
        ((ResultDirectory) newParent).add( p );
        return 0;
    }

    @Override
    public int rmdir( String path ) {
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
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        p.delete();
        return 0;
    }

    @Override
    public int open( String path, FuseFileInfo fi ) {
        return 0;
    }

    @Override
    public int write( String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi ) {
        ResultPath p = getPath( path );
        if ( p == null ) {
            return -ErrorCodes.ENOENT();
        }
        if ( !(p instanceof ResultFile) ) {
            return -ErrorCodes.EISDIR();
        }
        return ((ResultFile) p).write( buf, size, offset );
    }

    public void reset() {
        rootDirectory.contents.clear();
    }
}
