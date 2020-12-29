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

package org.polypheny.qtf.web;


import com.google.gson.Gson;
import java.net.URI;
import java.nio.ByteBuffer;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.polypheny.qtf.QueryInterface;
import org.polypheny.qtf.fuse.ResultFS;
import org.polypheny.qtf.fuse.ResultFS.ResultDirectory;
import org.polypheny.qtf.fuse.ResultFS.ResultFile;
import org.polypheny.qtf.web.Result.DbColumn;


@Slf4j
public class SocketClient extends WebSocketClient {

    private final ResultFS myFuse;
    private final Gson gson = new Gson();
    private final QueryInterface listener;

    public SocketClient( URI serverURI, ResultFS fuse, QueryInterface listener ) {
        super( serverURI );
        this.myFuse = fuse;
        this.listener = listener;
    }

    @Override
    public void onOpen( ServerHandshake handshakedata ) {
        //log.info( "Opened socket" );
    }

    @Override
    public void onClose( int code, String reason, boolean remote ) {
        log.info( "Closed socket" );
    }

    @Override
    public void onMessage( String message ) {
        Result result;
        try {
            Result[] results = gson.fromJson( message, Result[].class );
            result = results[0];
        } catch ( Throwable t ) {
            result = gson.fromJson( message, Result.class );
        }
        listener.onResultUpdate( result );
        if ( result.error != null ) {
            log.error( "The submitted query failed: " + result.error );
            return;
        }
        for ( int d = 0; d < result.data.length; d++ ) {
            ResultDirectory dir = new ResultDirectory( myFuse.getRootDirectory(), result, d );
            for ( int h = 0; h < result.header.length; h++ ) {
                DbColumn col = result.header[h];
                if ( result.data[d][h] == null ) {
                    continue;
                }
                switch ( col.dataType ) {
                    case "FILE":
                    case "IMAGE":
                    case "VIDEO":
                    case "SOUND":
                        dir.add( ResultFile.ofUrl( col.name, result.data[d][h], dir ) );
                        break;
                    default:
                        dir.add( ResultFile.ofData( col.name, result.data[d][h], dir ) );
                }
            }
            myFuse.add( dir );
        }
        myFuse.setResult( result );
    }

    @Override
    public void onMessage( ByteBuffer message ) {
        //log.info( "received ByteBuffer" );
    }

    @Override
    public void onError( Exception ex ) {
        log.error( "an error occurred:" + ex );
    }
}
