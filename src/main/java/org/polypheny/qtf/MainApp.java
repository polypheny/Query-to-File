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
    public void start( Stage primaryStage ) {
        Parent root = null;
        FXMLLoader loader = null;
        //from https://stackoverflow.com/questions/24254000/how-to-force-anti-aliasing-in-javafx-fonts
        System.setProperty( "prism.lcdtext", "false" );
        try {
            loader = new FXMLLoader( getClass().getResource( "/fxml/sample.fxml" ) );
            root = loader.load();
        } catch ( IOException e ) {
            log.error( "Could not load fxml", e );
            System.exit( 1 );
        }
        //see https://stackoverflow.com/questions/36981599/fxml-minheight-minwidth-attributs-ignored
        primaryStage.setMinWidth( root.minWidth( -1 ) );
        primaryStage.setMinHeight( root.minHeight( -1 ) );
        primaryStage.setTitle( "Polypheny-DB Query-To-File" );
        primaryStage.setScene( new Scene( root ) );
        primaryStage.show();
        //see https://stackoverflow.com/questions/44439408/javafx-controller-detect-when-stage-is-closing
        Controller controller = loader.getController();
        primaryStage.setOnHidden( e -> {
            controller.shutdown();
        } );
    }
}
