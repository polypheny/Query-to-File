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


import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Controller {

    final Connector connector;
    @FXML
    private TextArea console;

    public Controller() {
        this.connector = new Connector();
    }

    @FXML
    public void submit() {
        String query = console.getText();
        try ( Statement statement = connector.getStatement() ) {
            ResultSet rs = statement.executeQuery( query );
            while ( rs.next() ) {
                for ( int i = 0; i < rs.getMetaData().getColumnCount(); i++ ) {
                    System.out.print( "|" + rs.getString( i + 1 ) );
                }
                System.out.print( "|\n" );
            }
        } catch ( SQLException e ) {
            log.error( "SQL exception", e );
        }
    }
}
