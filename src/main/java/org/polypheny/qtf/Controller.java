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
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.qtf.fuse.ResultFS;
import org.polypheny.qtf.web.QueryRequest;
import org.polypheny.qtf.web.SocketClient;


@Slf4j
public class Controller {

    @FXML
    private TextArea console;
    private final ResultFS myFuse;
    private SocketClient socketClient;
    private final Gson gson = new Gson();

    public Controller() {
        this.myFuse = new ResultFS();
        File qtfRoot = new File( System.getProperty( "user.home" ), ".polypheny/qtf" );
        qtfRoot.mkdirs();
        //unmount in case it is still mounted
        myFuse.umount();
        myFuse.mount( qtfRoot.toPath() );

        try {
            this.socketClient = new SocketClient( new URI( QTFConfig.getWebSocketUrl() ), myFuse );
            log.info( "Connecting to websocket..." );
            socketClient.connectBlocking();
            log.info( "Established a connection with the websocket" );
        } catch ( URISyntaxException | InterruptedException e ) {
            log.error( "Could not connect to websocket.", e );
            System.exit( 1 );
        }
    }

    @FXML
    public void submit() {
        myFuse.reset();
        String query = console.getText();
        socketClient.send( gson.toJson( new QueryRequest( query ), QueryRequest.class ) );
        //the socketClient handles the asynchronous response
    }

    @FXML
    public void reset() {
        myFuse.reset();
    }

    public void shutdown() {
        myFuse.umount();
        socketClient.close();
    }

}
