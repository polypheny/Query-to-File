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

package org.polypheny.qtf;


import com.google.gson.Gson;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import jnr.ffi.Platform;
import jnr.ffi.Platform.OS;
import kong.unirest.MultipartBody;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.qtf.fuse.ResultFS;
import org.polypheny.qtf.web.BatchUpdateRequest;
import org.polypheny.qtf.web.BatchUpdateRequest.Update;
import org.polypheny.qtf.web.BatchUpdateRequest.Value;
import org.polypheny.qtf.web.Result;
import org.polypheny.qtf.web.SocketClient;
import org.polypheny.qtf.web.UIRequest.QueryRequest;
import org.polypheny.qtf.web.UIRequest.TableRequest;


@Slf4j
public abstract class QueryInterface {

    final ResultFS myFuse = new ResultFS();
    SocketClient socketClient;
    final Gson gson = new Gson();
    private final File root;

    public QueryInterface() {
        root = QTFConfig.getMountPoint();
        if ( Platform.getNativePlatform().getOS() != OS.WINDOWS ) {
            root.mkdirs();
        }

        //unmount in case it is still mounted
        //myFuse.umount();
        myFuse.mount( root.toPath(), false, false );
        try {
            this.socketClient = new SocketClient( new URI( QTFConfig.getWebSocketUrl() ), myFuse, this );
            log.info( "Connecting to websocket..." );
            if ( socketClient.connectBlocking() ) {
                log.info( "Established a connection with the websocket" );
            } else {
                log.error( "Could not connect to websocket." );
                System.exit( 1 );
            }
        } catch ( URISyntaxException | InterruptedException e ) {
            log.error( "Could not connect to websocket.", e );
            System.exit( 1 );
        }
    }

    void submitQueryRequest( String query ) {
        myFuse.reset();
        socketClient.send( gson.toJson( new QueryRequest( query ), QueryRequest.class ) );
        //the socketClient handles the asynchronous response
    }

    void submitTableRequest( String tableId ) {
        myFuse.reset();
        socketClient.send( gson.toJson( new TableRequest( tableId ), TableRequest.class ) );
    }

    public abstract void onResultUpdate( Result result );

    Result commit() {
        if ( myFuse.getResult() == null || myFuse.getResult().table == null ) {
            return new Result( "Cannot commit because of missing table name." );
        }
        BatchUpdateRequest request = myFuse.getBatchUpdateRequest();
        //multiPartContent: see https://github.com/Kong/unirest-java/issues/165
        MultipartBody body = Unirest.post( QTFConfig.getRestInterface( "batchUpdate" ) ).multiPartContent();
        int counter = 0;
        for ( Update update : request.getUpdates() ) {
            for ( Value value : update.getNewValues().values() ) {
                if ( value.getFile() != null ) {
                    value.setFileName( "file" + ++counter );
                    File f = new File( root, value.getFile().getPath() );
                    body.field( "file" + counter, f );
                }
            }
        }
        body.field( "request", gson.toJson( request, BatchUpdateRequest.class ) );
        return body.asObject( Result.class ).getBody();
    }

}
