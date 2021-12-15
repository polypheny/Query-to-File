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

package org.polypheny.qtf;


import java.io.IOException;
import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class MainApp extends Application {

    public static void run( String[] args ) {
        launch( args );
    }

    @Override
    public void start( Stage stage ) {
        Parent root = null;
        FXMLLoader loader = null;
        try {
            loader = new FXMLLoader( getClass().getResource( "/fxml/startup.fxml" ) );
            loader.setResources(ResourceBundle.getBundle("config"));
            root = loader.load();
        } catch ( IOException e ) {
            log.error( "Could not load fxml", e );
            System.exit( 1 );
        }

        stage.setMinWidth( root.minWidth( -1 ) );
        stage.setMinHeight( root.minHeight( -1 ) );
        stage.setTitle( "Edit Settings" );
        stage.setScene( new Scene( root ) );
        stage.show();
    }
}
