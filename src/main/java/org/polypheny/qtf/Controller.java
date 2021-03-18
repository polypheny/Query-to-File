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


import java.awt.Desktop;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;
import org.polypheny.qtf.web.Result;


@Slf4j
public class Controller extends QueryInterface {

    @FXML
    private TextArea console;
    @FXML
    private Label feedback;
    @FXML
    private TextField tableId;

    public Controller() {
        super();
    }

    @FXML
    public void submit() {
        if ( !tableId.getText().equals( "" ) ) {
            super.submitTableRequest( tableId.getText() );
        } else {
            String query = console.getText();
            super.submitQueryRequest( query );
        }
        feedback.setText( "Waiting for response" );
    }

    @Override
    public void onResultUpdate( Result result ) {
        final String message;
        if ( result.error != null ) {
            message = "The query failed";
        } else if ( result.data == null && result.affectedRows != null ) {
            message = String.format( "The query was successful and affected %d rows.", result.affectedRows );
        } else {
            message = String.format( "Fetched %d rows\n", result.data.length );
        }
        printFeedback( message );
    }

    @FXML
    public void onTableIdKeyUp() {
        if ( tableId.getText().equals( "" ) && console.isDisabled() ) {
            console.setDisable( false );
        } else if ( !tableId.getText().equals( "" ) && !console.isDisabled() ) {
            console.setDisable( true );
        }
    }

    @FXML
    public void onCommit() {
        Result result = super.commit();
        if ( result.error != null ) {
            printFeedback( "The commit failed. Check the console for more information." );
            log.error( "The commit failed: " + result.error );
        } else {
            if ( result.affectedRows == 1 ) {
                printFeedback( "The commit was successful. 1 row was affected." );
            } else {
                printFeedback( String.format( "The commit was successful. %d rows were affected.", result.affectedRows ) );
            }
        }
    }

    @FXML
    public void openInFolder() {
        //from https://stackoverflow.com/questions/12339922/opening-finder-explorer-using-java-swing
        if ( Desktop.isDesktopSupported() ) {
            try {
                Desktop.getDesktop().open( QTFConfig.getMountPoint() );
            } catch ( IOException e ) {
                log.error( "Could not open folder in Finder/Explorer", e );
            }
        }
    }

    public void shutdown() {
        myFuse.umount();
        socketClient.shutdown();
    }

    private void printFeedback( String msg ) {
        Platform.runLater( () -> feedback.setText( msg ) );
    }

}
