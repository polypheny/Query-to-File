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


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class Connector {

    Connection conn;

    public Connector() {
        connect( "localhost", 20591, "APP", "pa", "" );
    }

    private void connect( String dbHost, int port, String dbName, String user, String password ) {
        try {
            Class.forName( "ch.unibas.dmi.dbis.polyphenydb.jdbc.Driver" );
        } catch ( ClassNotFoundException e ) {
            log.error( "Polypheny-DB Driver not found", e );
        }
        String url = "jdbc:polypheny:http://" + dbHost + ":" + port;//+ "/" + dbName + "?prepareThreshold=0";
        log.debug( "Connecting to database @ {}", url );

        Properties props = new Properties();
        props.setProperty( "user", user );
        props.setProperty( "password", password );
        props.setProperty( "wire_protocol", "PROTO3" );
        try {
            conn = DriverManager.getConnection( url, props );
            conn.setAutoCommit( true );
        } catch ( SQLException e ) {
            log.error( "SQL exception", e );
        }
    }

    public Statement getStatement() throws SQLException {
        return conn.createStatement();
    }

}
